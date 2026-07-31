# rag-server

A retrieval-augmented-generation (RAG) question-answering service over a fixed corpus of documents,
written in Java 21 and deployed as a single AWS Lambda behind a public function URL.

It has two halves:

- **Ingestion** — an offline pipeline that takes a catalog of source documents, splits them into
  overlapping chunks, embeds each chunk, and loads the vectors into a vector store. This runs on a
  workstation, not in Lambda.
- **Query** — a Lambda that serves a small chat web page, embeds each incoming prompt, retrieves the
  most relevant chunks from the vector store, and asks an OpenAI chat model to answer using only
  those chunks, citing which ones it used.

Two corpora have been ingested so far: the Portland City Code (`portland-city-code`) and the Oregon
Revised Statutes (`oregon-state-code`).

## How it works

### Ingestion pipeline

`IngestionPipeline` runs five stages in order, each writing its output into a datastore so a rerun
can skip work that already exists:

```
sourceCatalog.json
      │
      ▼
SourceCatalogValidator   unique ids and source URLs
      │
      ▼
ManifestBuilder          copies source text into the ingestion store, writes ingestionManifest.json
      │                  and sourceRecordsDocument.json
      ▼
Chunker                  word-count chunks with percent overlap → chunkManifest.json + chunk .txt files
      │
      ▼
Embedder                 embeds every chunk on a thread pool → embedding .bin files
      │
      ▼
VectorStoreLoader        loads (vector, chunk) pairs into each configured vector store
```

Every stage is idempotent: it checks `exists(...)` before writing, so an interrupted run can be
restarted and will pick up where it stopped.

### Query path

`LambdaServer` is the Lambda entry point (`com.mgaray.ragserver.server.LambdaServer::handleRequest`).
A `GET` returns the chat page bundled at `src/main/resources/index.html`; a `POST` is handed to
`QueryHandler`, which:

1. Embeds the whole conversation and, separately, the most recent prompt, and pulls top-*k* chunks
   for each from the vector store.
2. Adds back chunks the model cited in earlier turns (up to a configured maximum).
3. Builds a prompt listing each chunk under a **random UUID** rather than its real id — a cited id
   that isn't in the lookup table is therefore provably a hallucination and is reported as such.
4. Asks the chat model to reply as JSON: `{ "dataSourcesUsed": [...], "response": "..." }`.
5. Returns the answer, the source URLs behind the cited chunks, and the updated session state.

Conversation state lives entirely in the client — it round-trips through the `sessionState` field of
the request and response, so the Lambda stays stateless. Optional encryption of that state is
implemented in `EncryptionDelegate`.

### Storage abstractions

Both the datastore and the vector store are interfaces with interchangeable implementations, which
is what lets the same pipeline code run locally and against AWS.

`IDatastore` — keyed byte storage, with default methods layering JSON, gzip, string and `float[]`
conveniences on top of three primitives (`read`, `write`, `exists`):

| Implementation | Backing |
| --- | --- |
| `InMemoryDatastore` | a map, for tests and as a cache tier |
| `LocalDiskDatastore` | a directory on disk |
| `S3Datastore` | an S3 bucket |
| `TieredDatastore` | composes the others, ordered most-volatile → least-volatile; reads fall through and backfill, writes fan out |

`IVectorStore<T>` — nearest-neighbour search over records:

| Implementation | Backing |
| --- | --- |
| `InMemoryVectorStore` | brute-force similarity search, exportable to a gzipped JSON file |
| `S3VectorStore` | AWS S3 Vectors (`s3vectors:`), with the chunk JSON carried in vector metadata |

## Repository layout

```
src/main/java/       code that ships in the Lambda zip
  Models.java          every record/enum in the system, in one file
  IngestionMain.java   ingestion entry point (S3 source → S3 ingestion + S3 Vectors)
  ingest/              Chunker, Embedder, ManifestBuilder, VectorStoreLoader, SourceCatalogValidator
  server/              LambdaServer, QueryHandler
  storage/             data/ (IDatastore + implementations), vector/, parameter/ (SSM)
  crypto/ logger/ util/
src/main/resources/
  index.html           the chat page, served by GET
src/tools/java/      developer tools; compiled separately and kept out of the Lambda zip
  LocalWebappMain      run the webapp locally against a local-disk corpus
  S3WebappMain         run the webapp locally against the S3 corpus
  LocalIngestionMonitorMain / S3IngestionMonitorMain
                       ingestion runs with a DatastoreMonitor printing read/write counters
  sourcecatalogdownloader/
                       corpus downloaders (Oregon statutes, Portland city code, two bible corpora)
  sourcecatalogwriter/ turns already-downloaded corpora into a sourceCatalog.json
  localpipeline/       small harnesses for exercising the Chunker and vector store by hand
  localserver/         a plain com.sun.net.httpserver wrapper used by the local mains
src/test/java/       JUnit 5 tests
terraform/           Lambda, function URL, IAM, SSM parameters; S3 backend for state
deploy.sh            build + terraform apply
deploy-secrets.sh    push secrets from local/config.sh into SSM
local/               gitignored: config.sh, downloaded corpora, local ingestion output
```

## Prerequisites

- JDK 21 (the Gradle toolchain will resolve one)
- An OpenAI API key
- For anything touching AWS: credentials with access to S3, S3 Vectors, SSM, Lambda and IAM, plus
  Terraform ≥ 1.11

## Configuration

Local runs and both deploy scripts read `local/config.sh`, which is gitignored and holds both the
secrets and the deployment settings:

```bash
export OPEN_AI_API_KEY="sk-..."
export SYMMETRIC_SIGNING_KEY="..."          # openssl rand -base64 32
export CHAT_MODEL_TYPE="OPEN_AI_GPT_5_NANO"
export VECTOR_QUERY_CONFIG='{"conversationChunkCount":10,"mostRecentPromptChunkCount":10,"conversationPreviouslyUsedChunkMaxCount":10}'
export INGESTION_MANIFEST_BUCKET="rag-server-ingestion"
export VECTOR_STORE_BUCKET="rag-server-vector"
export INGESTION_MANIFEST_ID="oregon-state-code"
export TERRAFORM_TFSTATE_S3_BUCKET="tfstate-store"
export TERRAFORM_TFSTATE_S3_REGION="us-west-2"
export DEPLOYMENT_REGION="us-west-2"
export LAMBDA_FUNCTION_NAME="rag-server-lambda"
```

The local mains read the two secrets straight out of this file via
`SsmDelegate.getParameterFromLocalConfig(...)`; in Lambda the same values come from SSM Parameter
Store. Everything else is passed to Terraform as `TF_VAR_*` and ends up as Lambda environment
variables.

Chunk size, overlap, embedding model, thread count and which corpus to ingest are compile-time
constants at the top of `IngestionMain`, shared by the ingestion mains in `src/tools`.

## Build and test

```bash
./gradlew build      # compiles, runs tests, and produces the Lambda zip
./gradlew test
./gradlew buildZip   # build/distributions/rag-server-lambda-<version>.zip
```

The zip puts compiled classes at the root and dependencies under `lib/`. The local ONNX embedding
model (BGE-small, ~135 MB of native binaries) stays on the normal classpath so offline runs work,
but is excluded from the zip — the deployed function is expected to use OpenAI embeddings.

The `tools` source set is compiled alongside `main` but never packaged, which is why jsoup (used
only by the downloaders) never reaches Lambda. There is no Gradle `run` task; the `main` methods in
`src/tools` are meant to be launched from the IDE.

## Running locally

1. Fill in `local/config.sh`.
2. Run one of:
   - `LocalWebappMain` — serves the webapp from a corpus already ingested to `local/s3bucket`, using
     an in-memory vector store loaded from the exported `vectorStore.json.gz`.
   - `S3WebappMain` — same webapp, but reading the ingestion datastore and vector store from S3.
3. Open `http://localhost` (both mains bind port 80, so they need privileges or a port change).

To ingest a corpus locally first, run `LocalIngestionMonitorMain`, which reads sources from
`local/sources`, writes everything under `local/s3bucket`, and prints datastore counters. `S3IngestionMonitorMain` and `IngestionMain` do the same against S3, the latter also
loading AWS S3 Vectors.

## Getting a corpus in

Ingestion starts from a `sourceCatalog.json`:

```json
{
  "title": "oregon-state-code",
  "sources": [
    {
      "id": "ors001",
      "sourceUrl": "https://www.oregonlegislature.gov/bills_laws/ors/ors001.html",
      "retrievedAt": "2025-07-20T00:00:00.000000+00:00",
      "title": "Chapter 1 — Courts and Judicial Officers",
      "location": "oregon-state-code/sources/ors001.txt"
    }
  ]
}
```

Ids and source URLs must each be unique — `SourceCatalogValidator` rejects the catalog otherwise.

Two routes produce one:

- **Downloaders** (`src/tools/.../sourcecatalogdownloader/`) fetch a corpus and write the catalog
  layout directly. `CorpusDownloader` holds the shared plumbing — throttled HTTP with retries, text
  normalization, catalog writing — while each corpus keeps its own `...Main` because discovery and
  HTML parsing rules differ. 

`CopySourceCatalogToS3` uploads a locally built catalog and its text files to the S3 source bucket.

## Deploy

One-time setup: create the S3 buckets named in `local/config.sh` (source, ingestion, vector) and the
Terraform state bucket, then ingest a corpus so the manifest and vectors the Lambda reads on startup
exist.

```bash
./deploy.sh                 # gradle build + terraform apply (extra args pass through, e.g. -auto-approve)
./deploy-secrets.sh         # push OPEN_AI_API_KEY and SYMMETRIC_SIGNING_KEY into SSM
```

Run `deploy.sh` first — Terraform creates the two SSM parameters as placeholders with
`ignore_changes = [value]`, and `deploy-secrets.sh` fills in the real values out of band so they
never enter Terraform state. `deploy.sh` initializes the Terraform S3 backend on first run only;
delete `terraform/.terraform` to force a re-init after a backend or provider change.

Terraform provisions the function, a public function URL (`authorization_type = "NONE"`), an IAM
role with scoped SSM/KMS and S3 Vectors read permissions, and attaches the AWS Parameters and
Secrets Lambda extension layer. The deployed URL is the `function_url` output.

## Notes

- `local/config.sh` holds a live OpenAI API key in plaintext. It is gitignored, but worth rotating
  if that file has ever been shared or copied off the machine.
- The function URL is unauthenticated, so anyone with the URL can spend OpenAI credits through it.
- The S3 IAM policy grants `GetObject`/`PutObject` on `arn:aws:s3:::*/*` — every bucket in the
  account.
