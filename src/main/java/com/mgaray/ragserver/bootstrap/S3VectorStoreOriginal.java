package com.mgaray.ragserver.bootstrap;

import com.mgaray.ragserver.common.JsonUtils;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.PutInputVector;
import software.amazon.awssdk.services.s3vectors.model.QueryOutputVector;
import software.amazon.awssdk.services.s3vectors.model.VectorData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A vector store backed by the native Amazon S3 Vectors service. Unlike {@link VectorStore}, which
 * serializes an in-memory index to a single S3 object and searches it in-process, this delegates both
 * storage and nearest-neighbour search to S3: {@code add} issues a PutVectors call and {@code get}
 * issues a QueryVectors call, so nothing needs to be held in memory.
 *
 * <p>The arbitrary payload object is stored alongside each vector as JSON in the vector's metadata under
 * {@link #METADATA_PAYLOAD_KEY} and reconstructed (as a generic {@code Map}) on the way out. Payloads are
 * expected to be small reference objects (e.g. a {@code Chunk}); a large payload should be configured as a
 * non-filterable metadata key when the index is created, since filterable metadata is size-limited.
 */
public class S3VectorStoreOriginal {

    private static final String METADATA_PAYLOAD_KEY = "payload";

    // The S3 Vectors client is thread-safe and expensive to create, so share a single lazily-initialized
    // instance, mirroring S3Utils.
    private static volatile S3VectorsClient s3VectorsClient;

    private final String vectorBucketName;
    private final String indexName;

    public S3VectorStoreOriginal(String vectorBucketName, String indexName) {
        this.vectorBucketName = vectorBucketName;
        this.indexName = indexName;
    }

    public void add(float[] vector, Object object) {
        Document metadata = Document.mapBuilder()
                .putString(METADATA_PAYLOAD_KEY, JsonUtils.toJson(object))
                .build();
        PutInputVector inputVector = PutInputVector.builder()
                .key(UUID.randomUUID().toString())
                .data(VectorData.builder().float32(toFloatList(vector)).build())
                .metadata(metadata)
                .build();
        client().putVectors(request -> request
                .vectorBucketName(vectorBucketName)
                .indexName(indexName)
                .vectors(inputVector));
    }

    public List<Object> get(float[] searchVector, int count) {
        List<QueryOutputVector> matches = client().queryVectors(request -> request
                        .vectorBucketName(vectorBucketName)
                        .indexName(indexName)
                        .topK(count)
                        .queryVector(VectorData.builder().float32(toFloatList(searchVector)).build())
                        .returnMetadata(true)
                        .returnDistance(true))
                .vectors();
        List<Object> objects = new ArrayList<>();
        for (QueryOutputVector match : matches) {
            String payloadJson = match.metadata().asMap().get(METADATA_PAYLOAD_KEY).asString();
            objects.add(JsonUtils.parse(payloadJson));
        }
        return objects;
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> floats = new ArrayList<>(vector.length);
        for (float value : vector) {
            floats.add(value);
        }
        return floats;
    }

    private static S3VectorsClient client() {
        S3VectorsClient result = s3VectorsClient;
        if (result == null) {
            synchronized (S3VectorStoreOriginal.class) {
                result = s3VectorsClient;
                if (result == null) {
                    result = S3VectorsClient.create();
                    s3VectorsClient = result;
                }
            }
        }
        return result;
    }
}
