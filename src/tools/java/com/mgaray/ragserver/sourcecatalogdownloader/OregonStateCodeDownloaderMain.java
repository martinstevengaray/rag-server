package com.mgaray.ragserver.sourcecatalogdownloader;

import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the Oregon Revised Statutes from the Oregon Legislature site directly into the source
 * catalog layout the rest of rag-server reads, replacing the two step pipeline
 * (rag-content-corpus-download/src/oregon-state-code/download-oregon-corpus.py, then
 * SourceCatalogWriter.sourceFolderForOregon).
 *
 * Only the chapter-level records the catalog needs are produced. The python script's section
 * splitting, per-chapter metadata files and resumable cache are not reproduced.
 *
 * Output, relative to the output datastore bucket:
 *   {sourceCatalogId}/sourceCatalog.json
 *   {sourceCatalogId}/sources/ors{NNN}.txt
 */
public class OregonStateCodeDownloaderMain {

    private static final String BASE_URL = "https://www.oregonlegislature.gov";
    private static final String LANDING_URL = BASE_URL + "/bills_laws/pages/ors.aspx";
    private static final String CHAPTER_BASE = BASE_URL + "/bills_laws/ors/";

    private static final String USER_AGENT =
            "Local-ORS-RAG-POC/0.1 contact=replace-with-your-email@example.com";
    private static final Duration THROTTLE = Duration.ofSeconds(1);

    /** Below this many chapters found via links, fall back to the published numeric ranges. */
    private static final int MINIMUM_DISCOVERED = 300;

    // The first chapter of each title. Requesting these with an uppercase .HTML extension has
    // historically returned a title-level table of contents that links every chapter in the title.
    private static final String[] TITLE_INDEX_STARTS = {
            "001", "012", "028", "040", "046", "051", "056", "071", "086",
            "090", "106", "111", "124", "131", "161", "171", "176", "186",
            "201", "221", "236", "246", "261", "270", "276", "284", "286A",
            "291", "305", "326", "366", "396", "406", "409", "426", "431",
            "455", "471", "476", "496", "506", "516", "526", "536", "561",
            "576", "596", "616", "645", "651", "670", "705", "706", "723",
            "731", "756", "776", "801", "830", "835",
    };

    // Numeric ranges from the official Table of Titles and Chapters, used only when link discovery
    // comes up short. Gaps are expected: candidates that 404 or do not look like chapters are skipped.
    private static final int[][] NUMERIC_TITLE_RANGES = {
            {1, 10}, {12, 25}, {28, 37}, {40, 45}, {46, 46}, {51, 55},
            {56, 70}, {71, 84}, {86, 88}, {90, 105}, {106, 110}, {111, 119},
            {124, 130}, {131, 153}, {161, 169}, {171, 174}, {176, 185},
            {186, 200}, {201, 215}, {221, 227}, {236, 244}, {246, 260},
            {261, 268}, {270, 275}, {276, 283}, {284, 285}, {286, 289},
            {291, 297}, {305, 324}, {326, 359}, {366, 391}, {396, 404},
            {406, 408}, {409, 423}, {426, 430}, {431, 454}, {455, 470},
            {471, 475}, {476, 480}, {496, 501}, {506, 513}, {516, 523},
            {526, 532}, {536, 558}, {561, 571}, {576, 587}, {596, 610},
            {616, 635}, {645, 650}, {651, 663}, {670, 704}, {705, 705},
            {706, 717}, {723, 726}, {731, 752}, {756, 774}, {776, 783},
            {801, 826}, {830, 830}, {835, 838},
    };

    private static final String[] SUFFIX_SEEDS =
            {"285A", "285B", "285C", "286A", "475A", "475B", "475C"};

    private static final Pattern CHAPTER_LINK =
            Pattern.compile("ors(\\d{1,3}[a-z]?)\\.html", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER_IDENTIFIER =
            Pattern.compile("0*(\\d{1,3})([A-Za-z]?)");

    // "Chapter 90 - Residential Landlord and Tenant". The separator is an em dash on most pages and
    // a period on the title-index pages; both are consumed so neither leaks into the title.
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "Chapter\\s+(\\d{1,3}[A-Z]?)\\s*(?:[.\\u2014\\u2013-]\\s*)?([^\\n]*)",
            Pattern.CASE_INSENSITIVE);

    // Content roots the Legislature's SharePoint templates have used, best first.
    private static final String[] CONTENT_SELECTORS = {
            "main", "article", "[role='main']", "#DeltaPlaceHolderMain", ".ms-rtestate-field", "#contentBox",
    };

    private final CorpusDownloader downloader = new CorpusDownloader(USER_AGENT, THROTTLE);
    private final IDatastore outputDatastore;

    public OregonStateCodeDownloaderMain(IDatastore outputDatastore) {
        this.outputDatastore = outputDatastore;
    }

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        IDatastore outputDatastore = new LocalDiskDatastore(outputBucket);
        //IDatastore outputDatastore = new S3Datastore("rag-server-source");

        String sourceCatalogId = "oregon-state-code";
        List<String> errors = new OregonStateCodeDownloaderMain(outputDatastore)
                .downloadSourceFolderForOregon(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

    public List<String> downloadSourceFolderForOregon(String sourceCatalogId) {
        List<String> chapters = discoverChapters();
        System.out.println("Found " + chapters.size() + " candidate chapters");

        List<Source> sources = new ArrayList<>();
        int skipped = 0;
        for (String chapter : chapters) {
            String slug = chapterSlug(chapter);
            String sourceUrl = CHAPTER_BASE + "ors" + slug + ".html";

            String html = downloader.fetchOptional(sourceUrl);
            downloader.throttle();
            if (html == null) {
                skipped++;
                continue;
            }

            String retrievedAt = CorpusDownloader.retrievedAtNow();
            //extractText strips the chrome from the document, so the title scan that follows it
            //only ever sees content nodes
            Document document = Jsoup.parse(html, sourceUrl);
            String text = extractText(document);

            // Candidates from the numeric fallback may resolve to title indexes or unrelated pages.
            if (!looksLikeChapter(text, chapter)) {
                skipped++;
                continue;
            }

            String sourceId = "ors" + slug;
            String textLocation = CorpusDownloader.sourceTextLocation(sourceCatalogId, sourceId);
            outputDatastore.writeString(textLocation, text);
            sources.add(new Source(sourceId, sourceUrl, retrievedAt, chapterTitle(document), textLocation));

            if (sources.size() % 50 == 0) {
                System.out.println("  downloaded " + sources.size() + " chapters");
            }
        }

        System.out.println("Downloaded " + sources.size() + " chapters, skipped " + skipped);
        return CorpusDownloader.writeCatalog(outputDatastore, sourceCatalogId, sources);
    }

    /**
     * Chapter identifiers linked from the landing page and the per-title index pages, in chapter
     * order. Falls back to the published numeric ranges only if link discovery comes up short.
     */
    private List<String> discoverChapters() {
        Set<String> discovered = new TreeSet<>(chapterOrder());

        List<String> discoveryUrls = new ArrayList<>();
        discoveryUrls.add(LANDING_URL);
        for (String start : TITLE_INDEX_STARTS) {
            //the uppercase extension is intentional; it is what serves the title-level table
            discoveryUrls.add(CHAPTER_BASE + "ors" + chapterSlug(start) + ".HTML");
        }

        for (String url : discoveryUrls) {
            String html = downloader.fetchOptional(url);
            downloader.throttle();
            if (html == null) {
                continue;
            }
            // Some SharePoint pages embed chapter urls in scripts rather than anchors, so the whole
            // page source is scanned rather than just its links.
            Matcher matcher = CHAPTER_LINK.matcher(html);
            while (matcher.find()) {
                normalizeChapter(matcher.group(1)).ifPresent(discovered::add);
            }
        }

        if (discovered.size() < MINIMUM_DISCOVERED) {
            System.out.println("Only " + discovered.size()
                    + " chapters discovered from index pages; adding the numeric title ranges");
            for (int[] range : NUMERIC_TITLE_RANGES) {
                for (int number = range[0]; number <= range[1]; number++) {
                    discovered.add(String.valueOf(number));
                }
            }
            for (String seed : SUFFIX_SEEDS) {
                normalizeChapter(seed).ifPresent(discovered::add);
            }
        }
        return new ArrayList<>(discovered);
    }

    /** Orders 1, 2, ..., 285A, 285B, ..., 286, matching the statute numbering. */
    private static Comparator<String> chapterOrder() {
        return Comparator
                .comparingInt((String chapter) -> Integer.parseInt(chapter.replaceAll("\\D", "")))
                .thenComparing(chapter -> chapter.replaceAll("\\d", ""));
    }

    private static java.util.Optional<String> normalizeChapter(String value) {
        Matcher matcher = CHAPTER_IDENTIFIER.matcher(CorpusDownloader.strip(value));
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                Integer.parseInt(matcher.group(1)) + matcher.group(2).toUpperCase(Locale.ROOT));
    }

    /** 90 becomes 090, 286A becomes 286a: zero padded number plus a lowercase suffix. */
    private static String chapterSlug(String chapter) {
        Matcher matcher = CHAPTER_IDENTIFIER.matcher(chapter);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid ORS chapter identifier: " + chapter);
        }
        return String.format("%03d", Integer.parseInt(matcher.group(1)))
                + matcher.group(2).toLowerCase(Locale.ROOT);
    }

    /**
     * Strips the page to readable text: drop the chrome, pick the content root that carries the
     * statute text, then collapse each line's whitespace and drop blank and repeated lines.
     */
    private static String extractText(Document document) {
        document.select("script, style, noscript, svg, nav, header, footer, form, aside, iframe")
                .forEach(Node::remove);

        Element root = chooseContentRoot(document);
        List<String> lines = new ArrayList<>();
        String previous = null;
        for (String line : CorpusDownloader.toLines(CorpusDownloader.textNodes(root, "\n"), true)) {
            //the templates repeat navigation and heading lines; collapse only immediate repeats
            if (line.equals(previous)) {
                continue;
            }
            lines.add(line);
            previous = line;
        }
        return String.join("\n", lines) + "\n";
    }

    private static Element chooseContentRoot(Document document) {
        Element best = null;
        int bestScore = -1;
        int bestLength = -1;
        for (String selector : CONTENT_SELECTORS) {
            for (Element candidate : document.select(selector)) {
                String text = candidate.text();
                int score = text.contains("Oregon Revised Statutes") || text.contains("EDITION") ? 1 : 0;
                if (score > bestScore || (score == bestScore && text.length() > bestLength)) {
                    best = candidate;
                    bestScore = score;
                    bestLength = text.length();
                }
            }
        }
        if (best != null) {
            return best;
        }
        return document.body() != null ? document.body() : document;
    }

    /** Guards against title indexes and error pages being stored as if they were chapters. */
    private static boolean looksLikeChapter(String text, String requestedChapter) {
        String expected = normalizeChapter(requestedChapter).orElse(requestedChapter);
        String head = text.substring(0, Math.min(text.length(), 12_000));

        Matcher heading = CHAPTER_HEADING.matcher(head);
        if (heading.find()) {
            java.util.Optional<String> found = normalizeChapter(heading.group(1));
            if (found.isPresent()) {
                return found.get().equals(expected);
            }
        }

        String compact = head.replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return compact.contains("OREGON REVISED STATUTES")
                && Pattern.compile("\\bCHAPTER\\s+0*" + Pattern.quote(expected) + "\\b")
                        .matcher(compact).find();
    }

    /**
     * The chapter title from its heading, for example "Residential Landlord and Tenant".
     *
     * Read from the dom rather than from the extracted text. The source hard-wraps headings, so in
     * the flattened text a title is split across lines and cannot be told apart from the banner or
     * the amendment notice that follows it. A single text node holds the whole heading and nothing
     * else, so normalizing that node's whitespace yields the title exactly.
     */
    private static String chapterTitle(Document document) {
        String title = "";
        for (Element element : document.getAllElements()) {
            for (Node child : element.childNodes()) {
                if (!(child instanceof TextNode textNode) || !title.isEmpty()) {
                    continue;
                }
                String line = CorpusDownloader.strip(
                        WHITESPACE.matcher(textNode.getWholeText()).replaceAll(" "));
                Matcher heading = CHAPTER_HEADING.matcher(line);
                if (heading.lookingAt()) {
                    title = CorpusDownloader.strip(heading.group(2));
                }
            }
        }
        return title;
    }

    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

}
