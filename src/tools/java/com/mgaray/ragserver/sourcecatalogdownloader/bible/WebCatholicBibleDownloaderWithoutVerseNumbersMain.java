package com.mgaray.ragserver.sourcecatalogdownloader.bible;

import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the World English Bible Catholic Edition from eBible.org directly into the source
 * catalog layout the rest of rag-server reads, replacing the two step pipeline
 * (rag-content-corpus-download/src/web-catholic-bible/download-web-bible-corpus.py, then
 * SourceCatalogWriter.sourceFolderForNabAndWebc).
 *
 * Only the chapter-level records the catalog needs are produced. The python script's verse
 * splitting, front matter records and per-page metadata are not reproduced.
 *
 * The World English Bible is in the Public Domain; "World English Bible" is a trademark of
 * eBible.org.
 *
 * Output, relative to the output datastore bucket:
 *   {sourceCatalogId}/sourceCatalog.json
 *   {sourceCatalogId}/sources/webc-{book}-{chapter}.txt
 */
public class WebCatholicBibleDownloaderWithoutVerseNumbersMain {

    private static final String INDEX_URL = "https://ebible.org/eng-web-c/index.htm";

    private static final String USER_AGENT =
            "WEBC-RAG-POC/1.0 contact=replace-with-your-email@example.com";
    private static final Duration THROTTLE = Duration.ofSeconds(1);

    // Chapter pages are <USFM code><chapter>.htm (GEN01.htm, 1SA09.htm, PSA119.htm). Each book also
    // has a chapter-list page named by bare code (GEN.htm), crawled for links but not kept, because
    // chapter pages only link to their immediate neighbours.
    private static final Pattern CHAPTER_FILE =
            Pattern.compile("([0-9A-Z]{3})(\\d{1,3})\\.htm", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOK_FILE =
            Pattern.compile("([0-9A-Z]{3})\\.htm", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAIN_TITLE_CLASS = Pattern.compile("mt\\d?");
    private static final Pattern CHAPTER_LABEL_CLASS = Pattern.compile("chapterlabel|^c$");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    /** Classes eBible.org uses for material that is not scripture text. */
    private static final String STRIP_SELECTORS = ".tnav, .dnav, .navnote, .copyright, .footnote, "
            + ".footnotes, .crossreference, a.notemark, span.notemark, .popup";

    private static final Set<String> HEADING_CLASSES =
            Set.of("s", "s1", "s2", "ms", "ms1", "mr", "sr", "d");

    /** Codes whose pages are supporting material rather than scripture chapters. */
    private static final Set<String> NON_SCRIPTURE_CODES = Set.of("FRT", "GLO");

    // USFM book codes for the 73 book Catholic canon plus the deuterocanonical Greek expansions and
    // the front matter eBible.org ships. Insertion order defines canonical output order.
    private static final Map<String, String> BOOK_CODES = new LinkedHashMap<>();
    static {
        String[][] books = {
                {"FRT", "Front Matter"}, {"GEN", "Genesis"}, {"EXO", "Exodus"}, {"LEV", "Leviticus"},
                {"NUM", "Numbers"}, {"DEU", "Deuteronomy"}, {"JOS", "Joshua"}, {"JDG", "Judges"},
                {"RUT", "Ruth"}, {"1SA", "1 Samuel"}, {"2SA", "2 Samuel"}, {"1KI", "1 Kings"},
                {"2KI", "2 Kings"}, {"1CH", "1 Chronicles"}, {"2CH", "2 Chronicles"}, {"EZR", "Ezra"},
                {"NEH", "Nehemiah"}, {"TOB", "Tobit"}, {"JDT", "Judith"}, {"EST", "Esther"},
                {"ESG", "Esther (Greek)"}, {"1MA", "1 Maccabees"}, {"2MA", "2 Maccabees"}, {"JOB", "Job"},
                {"PSA", "Psalms"}, {"PRO", "Proverbs"}, {"ECC", "Ecclesiastes"}, {"SNG", "Song of Songs"},
                {"WIS", "Wisdom"}, {"SIR", "Sirach"}, {"ISA", "Isaiah"}, {"JER", "Jeremiah"},
                {"LAM", "Lamentations"}, {"BAR", "Baruch"}, {"LJE", "Letter of Jeremiah"},
                {"EZK", "Ezekiel"}, {"DAN", "Daniel"}, {"DAG", "Daniel (Greek)"},
                {"S3Y", "Song of the Three Young Men"}, {"SUS", "Susanna"}, {"BEL", "Bel and the Dragon"},
                {"HOS", "Hosea"}, {"JOL", "Joel"}, {"AMO", "Amos"}, {"OBA", "Obadiah"}, {"JON", "Jonah"},
                {"MIC", "Micah"}, {"NAM", "Nahum"}, {"HAB", "Habakkuk"}, {"ZEP", "Zephaniah"},
                {"HAG", "Haggai"}, {"ZEC", "Zechariah"}, {"MAL", "Malachi"}, {"MAT", "Matthew"},
                {"MRK", "Mark"}, {"LUK", "Luke"}, {"JHN", "John"}, {"ACT", "Acts"}, {"ROM", "Romans"},
                {"1CO", "1 Corinthians"}, {"2CO", "2 Corinthians"}, {"GAL", "Galatians"},
                {"EPH", "Ephesians"}, {"PHP", "Philippians"}, {"COL", "Colossians"},
                {"1TH", "1 Thessalonians"}, {"2TH", "2 Thessalonians"}, {"1TI", "1 Timothy"},
                {"2TI", "2 Timothy"}, {"TIT", "Titus"}, {"PHM", "Philemon"}, {"HEB", "Hebrews"},
                {"JAS", "James"}, {"1PE", "1 Peter"}, {"2PE", "2 Peter"}, {"1JN", "1 John"},
                {"2JN", "2 John"}, {"3JN", "3 John"}, {"JUD", "Jude"}, {"REV", "Revelation"},
                {"GLO", "Glossary"},
        };
        for (String[] book : books) {
            BOOK_CODES.put(book[0], book[1]);
        }
    }

    private static final List<String> BOOK_ORDER = List.copyOf(BOOK_CODES.keySet());

    private final CorpusDownloader downloader = new CorpusDownloader(USER_AGENT, THROTTLE);
    private final IDatastore outputDatastore;

    public WebCatholicBibleDownloaderWithoutVerseNumbersMain(IDatastore outputDatastore) {
        this.outputDatastore = outputDatastore;
    }

    public static void main(String[] args) {
        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        IDatastore outputDatastore = new LocalDiskDatastore(outputBucket);
        //IDatastore outputDatastore = new S3Datastore("rag-server-source");

        String sourceCatalogId = "web-catholic-bible";
        List<String> errors = new WebCatholicBibleDownloaderWithoutVerseNumbersMain(outputDatastore)
                .downloadSourceFolderForWebc(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

    private record Link(String url, String bookCode, Integer chapter) {}

    private record Chapter(String bookCode, String book, int chapter, String text) {}

    public List<String> downloadSourceFolderForWebc(String sourceCatalogId) {
        // The index links into each book, and each chapter page links to its neighbours, so
        // discovery crawls outward breadth first rather than reading one link list.
        Deque<Link> queue = new ArrayDeque<>(links(downloader.fetch(INDEX_URL), INDEX_URL));
        downloader.throttle();
        if (queue.isEmpty()) {
            throw new IllegalStateException("No chapter links found on " + INDEX_URL
                    + "; the source html structure may have changed");
        }

        Set<String> seen = new LinkedHashSet<>();
        for (Link link : queue) {
            seen.add(link.url());
        }

        List<Chapter> chapters = new ArrayList<>();
        Map<String, String> retrievedAtByUrl = new LinkedHashMap<>();
        Map<String, String> urlByChapterKey = new LinkedHashMap<>();

        while (!queue.isEmpty()) {
            Link link = queue.removeFirst();
            String html = downloader.fetchOptional(link.url());
            downloader.throttle();
            if (html == null) {
                continue;
            }
            String retrievedAt = CorpusDownloader.retrievedAtNow();

            for (Link found : links(html, link.url())) {
                if (seen.add(found.url())) {
                    queue.addLast(found);
                }
            }

            // Book chapter-list pages exist only to be crawled; they hold no scripture of their own.
            if (link.chapter() == null) {
                continue;
            }

            Chapter chapter = parseChapter(
                    Jsoup.parse(html, link.url()), link.bookCode(), link.chapter());
            if (chapter == null) {
                continue;
            }
            chapters.add(chapter);
            String key = chapter.bookCode() + ":" + chapter.chapter();
            retrievedAtByUrl.put(key, retrievedAt);
            urlByChapterKey.put(key, link.url());

            if (chapters.size() % 100 == 0) {
                System.out.println("  downloaded " + chapters.size() + " chapters ("
                        + seen.size() + " pages discovered)");
            }
        }

        chapters.sort(Comparator
                .comparingInt((Chapter chapter) -> {
                    int index = BOOK_ORDER.indexOf(chapter.bookCode());
                    return index < 0 ? BOOK_ORDER.size() : index;
                })
                .thenComparingInt(Chapter::chapter));

        List<Source> sources = new ArrayList<>();
        for (Chapter chapter : chapters) {
            String key = chapter.bookCode() + ":" + chapter.chapter();
            String sourceId = "webc-" + slugify(chapter.book()) + "-" + chapter.chapter();
            String textLocation = CorpusDownloader.sourceTextLocation(sourceCatalogId, sourceId);
            outputDatastore.writeString(textLocation, chapter.text());
            sources.add(new Source(
                    sourceId,
                    urlByChapterKey.get(key),
                    retrievedAtByUrl.get(key),
                    chapter.book() + " " + chapter.chapter(),
                    textLocation));
        }

        System.out.println("Downloaded " + sources.size() + " chapters from "
                + seen.size() + " discovered pages");
        return CorpusDownloader.writeCatalog(outputDatastore, sourceCatalogId, sources);
    }

    /** Same-directory chapter and book links on a page, in document order. */
    private static List<Link> links(String html, String pageUrl) {
        Document document = Jsoup.parse(html, pageUrl);
        Map<String, Link> found = new LinkedHashMap<>();
        for (Element anchor : document.select("a[href]")) {
            String absolute = anchor.absUrl("href");
            if (absolute.isEmpty()) {
                continue;
            }
            String clean = absolute.replaceAll("[?#].*$", "");
            Link link = classify(clean, pageUrl);
            if (link != null) {
                found.putIfAbsent(clean, link);
            }
        }
        return new ArrayList<>(found.values());
    }

    /** Classifies a same-site, same-directory link as a chapter page or a book index page. */
    private static Link classify(String url, String pageUrl) {
        URI target = URI.create(url);
        URI base = URI.create(pageUrl);
        if (target.getHost() == null || !target.getHost().equalsIgnoreCase(base.getHost())) {
            return null;
        }
        if (!parentPath(target.getPath()).equals(parentPath(base.getPath()))) {
            return null;
        }

        String name = fileName(target.getPath());
        Matcher chapter = CHAPTER_FILE.matcher(name);
        if (chapter.matches()) {
            return new Link(url, chapter.group(1).toUpperCase(Locale.ROOT),
                    Integer.parseInt(chapter.group(2)));
        }
        Matcher book = BOOK_FILE.matcher(name);
        if (book.matches()) {
            return new Link(url, book.group(1).toUpperCase(Locale.ROOT), null);
        }
        return null;
    }

    private static String parentPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** Returns the chapter this page holds, or null for front matter and the glossary. */
    private static Chapter parseChapter(Document document, String bookCode, int chapterFromName) {
        if (NON_SCRIPTURE_CODES.contains(bookCode)) {
            return null;
        }

        Element main = document.selectFirst("div.main");
        if (main == null) {
            main = document.body() != null ? document.body() : document;
        }

        main.select(STRIP_SELECTORS).forEach(Node::remove);

        //the book title div appears on first-chapter pages; it is not part of the chapter text
        for (Element division : main.select("div[class]")) {
            String className = division.className().trim();
            if (MAIN_TITLE_CLASS.matcher(className).matches()) {
                division.remove();
            }
        }
        for (Element division : main.select("div[class]")) {
            for (String className : division.classNames()) {
                if (HEADING_CLASSES.contains(className)) {
                    division.remove();
                    break;
                }
            }
        }
        for (Element element : main.select("[class]")) {
            for (String className : element.classNames()) {
                if (CHAPTER_LABEL_CLASS.matcher(className).find()) {
                    element.remove();
                    break;
                }
            }
        }

        //verse numbers are <span class="verse">, the only remaining citation markers on the page
        main.select("span.verse").forEach(Node::remove);

        // div.p groups several verses into a real paragraph and div.q/div.q2 carry poetry lines, so
        // reading block by block gives prose and keeps the psalms' line breaks.
        String text = String.join("\n", CorpusDownloader.blockTexts(main));
        if (text.isEmpty()) {
            return null;
        }
        String book = BOOK_CODES.getOrDefault(bookCode, bookCode);
        return new Chapter(bookCode, book, chapterFromName, text);
    }


    private static String slugify(String value) {
        String slug = NON_SLUG.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        return slug.isEmpty() ? "unknown" : slug;
    }

}
