package com.mgaray.ragserver.sourcecatalogdownloader;

import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.time.Duration;
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

    // Portland is a government site: pause between title downloads rather than hammering it.
    private static final Duration THROTTLE = Duration.ofSeconds(1);

    // Index links to its titles as /code/1, /code/16, /code/33. Hrefs are relative today, but the
    // absolute form is matched too so a site change cannot silently drop titles from the catalog.
    private static final Pattern TITLE_PATH =
            Pattern.compile("(?:https?://(?:www\\.)?portland\\.gov)?/code/(\\d+)");

    private final CorpusDownloader downloader = new CorpusDownloader(USER_AGENT, THROTTLE);
    private final IDatastore outputDatastore;

    public PortlandCityCodeDownloaderMain(IDatastore outputDatastore) {
        this.outputDatastore = outputDatastore;
    }

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        IDatastore outputDatastore = new LocalDiskDatastore(outputBucket);
        //IDatastore outputDatastore = new S3Datastore("rag-server-source");

        String sourceCatalogId = "portland-city-code";
        List<String> errors = new PortlandCityCodeDownloaderMain(outputDatastore)
                .downloadSourceFolderForPortland(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

    public List<String> downloadSourceFolderForPortland(String sourceCatalogId) {
        List<Integer> titleNumbers = fetchTitleNumbers();
        System.out.println("Found " + titleNumbers.size() + " code titles");

        List<Source> sources = new ArrayList<>();
        for (int titleNumber : titleNumbers) {
            String sourceUrl = BASE_URL + "/code/" + titleNumber + "/all";
            System.out.println("Downloading Title " + titleNumber + ": " + sourceUrl);

            String html = downloader.fetch(sourceUrl);
            String retrievedAt = CorpusDownloader.retrievedAtNow();

            Document document = Jsoup.parse(html, sourceUrl);
            String pageTitle = document.title();
            String text = extractText(document);

            // The python downloader names files title-NN, SourceCatalogWriter turns that into
            // source id titleNN at location {catalog}/sources/NN.txt. Same ids and paths here.
            String sourceRecordId = String.format("%02d", titleNumber);
            String textLocation = CorpusDownloader.sourceTextLocation(sourceCatalogId, sourceRecordId);
            outputDatastore.writeString(textLocation, text);

            sources.add(new Source(
                    "title" + sourceRecordId,
                    sourceUrl,
                    retrievedAt,
                    pageTitle,
                    textLocation));

            downloader.throttle();
        }

        return CorpusDownloader.writeCatalog(outputDatastore, sourceCatalogId, sources);
    }

    /** Title numbers linked from the code index, ascending. Portland skips 8, so this is not 1..n. */
    private List<Integer> fetchTitleNumbers() {
        Document indexDocument = Jsoup.parse(downloader.fetch(INDEX_URL), INDEX_URL);
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
        return String.join("\n",
                CorpusDownloader.toLines(CorpusDownloader.textNodes(content, "\n"), false));
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

}
