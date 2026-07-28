package com.mgaray.ragserver.sourcecatalogdownloader;

import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.ingest.SourceCatalogValidator;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the Portland city code from portland.gov directly into the source catalog layout the
 * rest of rag-server reads, collapsing the two step pipeline
 * (rag-content-corpus-download/src/portland_city_code/download_corpus.py, then
 * SourceCatalogWriter.sourceFolderForPortland) into a single program. The intermediate
 * html/json/txt download folder is never written.
 *
 * Output, relative to the output datastore bucket:
 *   {sourceCatalogId}/sourceCatalog.json
 *   {sourceCatalogId}/sources/{NN}.txt
 */
public class PortlandCityCodeDownloaderMain {

    private static final String BASE_URL = "https://www.portland.gov";
    private static final String INDEX_URL = BASE_URL + "/code";

    // Identify the downloader honestly; kept identical to the python downloader's header.
    private static final String USER_AGENT = "Local-RAG-POC/0.1 contact=your-email@example.com";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRIES = 4;
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500, 502, 503, 504);

    // Portland is a government site: pause between title downloads rather than hammering it.
    private static final Duration THROTTLE = Duration.ofSeconds(1);

    // Index links to its titles as /code/1, /code/16, /code/33. Hrefs are relative today, but the
    // absolute form is matched too so a site change cannot silently drop titles from the catalog.
    private static final Pattern TITLE_PATH =
            Pattern.compile("(?:https?://(?:www\\.)?portland\\.gov)?/code/(\\d+)");

    // Matches the python downloader's datetime.now(timezone.utc).isoformat(), e.g.
    // 2026-07-16T17:04:27.201251+00:00, so retrievedAt stays comparable across both pipelines.
    private static final DateTimeFormatter RETRIEVED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'");

    private final IDatastore outputDatastore;
    private final HttpClient httpClient;

    public PortlandCityCodeDownloaderMain(IDatastore outputDatastore) {
        this.outputDatastore = outputDatastore;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        IDatastore outputDatastore = new LocalDiskDatastore(outputBucket);
        //IDatastore outputDatastore = new S3Datastore("rag-server-source");

        String sourceCatalogId = "portland-city-code";
        PortlandCityCodeDownloaderMain downloader = new PortlandCityCodeDownloaderMain(outputDatastore);
        List<String> errors = downloader.downloadSourceFolderForPortland(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

    public List<String> downloadSourceFolderForPortland(String sourceCatalogId) {
        List<Integer> titleNumbers = fetchTitleNumbers();
        System.out.println("Found " + titleNumbers.size() + " code titles");

        List<Source> sources = new ArrayList<>();
        for (int titleNumber : titleNumbers) {
            String sourceUrl = BASE_URL + "/code/" + titleNumber + "/all";
            System.out.println("Downloading Title " + titleNumber + ": " + sourceUrl);

            String html = fetch(sourceUrl);
            String retrievedAt = OffsetDateTime.now(ZoneOffset.UTC).format(RETRIEVED_AT);

            Document document = Jsoup.parse(html, sourceUrl);
            String pageTitle = document.title();
            String text = extractText(document);

            // The python downloader names files title-NN, SourceCatalogWriter turns that into
            // source id titleNN at location {catalog}/sources/NN.txt. Same ids and paths here.
            String sourceRecordId = String.format("%02d", titleNumber);
            String textLocation = originalSourceTextLocation(sourceCatalogId, sourceRecordId);
            outputDatastore.writeString(textLocation, text);

            sources.add(new Source(
                    "title" + sourceRecordId,
                    sourceUrl,
                    retrievedAt,
                    pageTitle,
                    textLocation));

            sleep(THROTTLE);
        }

        SourceCatalog sourceCatalog = new SourceCatalog(sourceCatalogId, sources);
        outputDatastore.writeObject(sourceCatalogLocation(sourceCatalogId), sourceCatalog);
        return SourceCatalogValidator.validate(sourceCatalog);
    }

    /** Title numbers linked from the code index, ascending. Portland skips 8, so this is not 1..n. */
    private List<Integer> fetchTitleNumbers() {
        Document indexDocument = Jsoup.parse(fetch(INDEX_URL), INDEX_URL);
        Set<Integer> titleNumbers = new HashSet<>();
        for (Element anchor : indexDocument.select("a[href]")) {
            Matcher matcher = TITLE_PATH.matcher(stripTrailingSlashes(anchor.attr("href")));
            if (matcher.matches()) {
                titleNumbers.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return titleNumbers.stream().sorted(Comparator.naturalOrder()).toList();
    }

    /**
     * Strips the page down to readable text, mirroring the python downloader's BeautifulSoup pass:
     * drop the chrome elements, keep the main element when present, then take every remaining text
     * node, trim it, and drop the blank lines.
     */
    private static String extractText(Document document) {
        document.select("script, style, nav, header, footer, form, noscript").forEach(Node::remove);

        Element content = document.selectFirst("main");
        if (content == null) {
            content = document;
        }

        List<String> textNodes = new ArrayList<>();
        content.traverse((node, depth) -> {
            if (node instanceof TextNode textNode) {
                textNodes.add(textNode.getWholeText());
            }
        });

        List<String> lines = new ArrayList<>();
        for (String textNode : textNodes) {
            for (String line : LINE_BREAK.split(textNode, -1)) {
                String stripped = strip(line);
                if (!stripped.isEmpty()) {
                    lines.add(stripped);
                }
            }
        }
        return String.join("\n", lines);
    }

    private String fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        RuntimeException lastFailure = new RuntimeException("No attempt was made for " + url);
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                sleep(Duration.ofSeconds(1L << (attempt - 1))); //exponential backoff: 1s, 2s, 4s, 8s
            }
            try {
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (RETRYABLE_STATUS_CODES.contains(response.statusCode())) {
                    lastFailure = new RuntimeException(
                            "HTTP " + response.statusCode() + " for " + url);
                    continue;
                }
                if (response.statusCode() >= 400) {
                    throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (java.io.IOException e) {
                lastFailure = new RuntimeException("Request failed for " + url, e);
            }
        }
        throw lastFailure;
    }

    private static String sourceCatalogLocation(String sourceCatalogId) {
        return sourceCatalogId + "/sourceCatalog.json";
    }

    private static String originalSourceTextLocation(String sourceManifestId, String sourceId) {
        return sourceManifestId + "/sources/" + sourceId + ".txt";
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|[\\n\\r]");

    /**
     * Trims a line, treating the non-breaking spaces that &nbsp; and friends decode to as
     * whitespace. String.strip() does not: Character.isWhitespace excludes U+00A0/U+2007/U+202F.
     * Without this the corpus keeps ~669 lines that hold nothing but a non-breaking space.
     */
    private static String strip(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(char character) {
        return Character.isWhitespace(character)
                || Character.isSpaceChar(character) //non-breaking spaces, which isWhitespace excludes
                || character == '\u0085';
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

}
