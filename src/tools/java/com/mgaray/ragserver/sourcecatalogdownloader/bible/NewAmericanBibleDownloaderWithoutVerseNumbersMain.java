package com.mgaray.ragserver.sourcecatalogdownloader.bible;

import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.storage.data.IDatastore;
import com.mgaray.ragserver.storage.data.LocalDiskDatastore;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the New American Bible from the Vatican's IntraText archive directly into the source
 * catalog layout the rest of rag-server reads, replacing the two step pipeline
 * (rag-content-corpus-download/src/new-american-bible/download-bible-corpus.py, then
 * SourceCatalogWriter.sourceFolderForNabAndWebc).
 *
 * Only the chapter-level records the catalog needs are produced. The python script's verse
 * splitting, introduction records and per-page metadata are not reproduced.
 *
 * This is the pre-NABRE archive transcribed 2002-11-11, not the current NABRE text.
 *
 * IMPORTANT: the NAB is copyrighted. ACKNOWLEDGE_PERMISSION below must be set deliberately, and
 * only when a licence or written permission covers downloading, storing and using this text.
 *
 * Output, relative to the output datastore bucket:
 *   {sourceCatalogId}/sourceCatalog.json
 *   {sourceCatalogId}/sources/nab-{book}-{chapter}.txt
 */
public class NewAmericanBibleDownloaderWithoutVerseNumbersMain {

    /** Set to true only when your permission or licence covers this download and its intended use. */
    private static final boolean ACKNOWLEDGE_PERMISSION = false;

    private static final String INDEX_URL = "https://www.vatican.va/archive/ENG0839/_INDEX.HTM";

    private static final String USER_AGENT =
            "NAB-RAG-POC/0.1 contact=replace-with-your-email@example.com";
    private static final Duration THROTTLE = Duration.ofSeconds(1);

    private static final Pattern PAGE_LINK =
            Pattern.compile("/archive/ENG0839/__P[0-9A-Z]+\\.HTM$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAPTER_LABEL =
            Pattern.compile("Chapter\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PSALM_LABEL =
            Pattern.compile("Psalm\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSE_LINE = Pattern.compile("(\\d{1,3})(?:[.)]|\\s+)?(.*)", Pattern.DOTALL);
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    /** Sentinel standing in for the <hr> rules the archive uses to separate page regions. */
    private static final String RULE = "__HR__";

    private static final String[] BOOKS = {
            "Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy",
            "Joshua", "Judges", "Ruth", "1 Samuel", "2 Samuel",
            "1 Kings", "2 Kings", "1 Chronicles", "2 Chronicles",
            "Ezra", "Nehemiah", "Tobit", "Judith", "Esther",
            "1 Maccabees", "2 Maccabees", "Job", "Psalms", "Proverbs",
            "Ecclesiastes", "Song of Songs", "The Song of Songs",
            "Wisdom", "The Book of Wisdom", "Sirach",
            "Isaiah", "Jeremiah", "Lamentations", "Baruch", "Ezekiel",
            "Daniel", "Hosea", "Joel", "Amos", "Obadiah", "Jonah",
            "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai",
            "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John",
            "Acts", "Acts of the Apostles", "Romans", "1 Corinthians",
            "2 Corinthians", "Galatians", "Ephesians", "Philippians",
            "Colossians", "1 Thessalonians", "2 Thessalonians",
            "1 Timothy", "2 Timothy", "Titus", "Philemon", "Hebrews",
            "James", "1 Peter", "2 Peter", "1 John", "2 John", "3 John",
            "Jude", "Revelation",
    };

    // A few books carry an article or a "The Book of" prefix in their page headers.
    private static final Map<String, String> BOOK_ALIASES = Map.of(
            "Acts of the Apostles", "Acts",
            "The Song of Songs", "Song of Songs",
            "The Book of Wisdom", "Wisdom");

    private static final Set<String> SINGLE_CHAPTER_BOOKS =
            Set.of("Philemon", "2 John", "3 John", "Jude", "Obadiah");

    private static final Set<String> NAVIGATION_PHRASES = Set.of(
            "previous", "next", "up", "back", "help",
            "click here to show the links to concordance",
            "intratext - text", "new american bible");

    private static final Map<String, String> BOOK_BY_LOWERCASE = new HashMap<>();
    static {
        for (String book : BOOKS) {
            BOOK_BY_LOWERCASE.put(book.toLowerCase(Locale.ROOT), book);
        }
    }

    private final CorpusDownloader downloader = new CorpusDownloader(USER_AGENT, THROTTLE);
    private final IDatastore outputDatastore;

    public NewAmericanBibleDownloaderWithoutVerseNumbersMain(IDatastore outputDatastore) {
        this.outputDatastore = outputDatastore;
    }

    public static void main(String[] args) {
        if (!ACKNOWLEDGE_PERMISSION) {
            System.err.println("Refusing to run: the New American Bible is copyrighted. Set "
                    + "ACKNOWLEDGE_PERMISSION to true only when your permission or licence covers "
                    + "downloading, storing and using this text for your intended purpose.");
            return;
        }

        String outputBucket = "/Users/turtlemccully/projects/rag-server/local/sources";
        IDatastore outputDatastore = new LocalDiskDatastore(outputBucket);
        //IDatastore outputDatastore = new S3Datastore("rag-server-source");

        String sourceCatalogId = "new-american-bible";
        List<String> errors = new NewAmericanBibleDownloaderWithoutVerseNumbersMain(outputDatastore)
                .downloadSourceFolderForNab(sourceCatalogId);
        System.out.println(sourceCatalogId + " errors: " + errors);
    }

    public List<String> downloadSourceFolderForNab(String sourceCatalogId) {
        List<String> pageUrls = discoverPages();
        System.out.println("Discovered " + pageUrls.size() + " source pages");

        List<Source> sources = new ArrayList<>();
        int skipped = 0;
        for (String pageUrl : pageUrls) {
            String html = downloader.fetchOptional(pageUrl);
            downloader.throttle();
            if (html == null) {
                skipped++;
                continue;
            }

            String retrievedAt = CorpusDownloader.retrievedAtNow();
            Chapter chapter = parseChapter(Jsoup.parse(html, pageUrl));

            // Book introductions and front matter are not chapters and are not carried into the
            // catalog, matching what SourceCatalogWriter consumed from chapters.jsonl.
            if (chapter == null) {
                skipped++;
                continue;
            }

            String sourceId = "nab-" + slugify(chapter.book()) + "-" + chapter.chapter();
            String textLocation = CorpusDownloader.sourceTextLocation(sourceCatalogId, sourceId);
            outputDatastore.writeString(textLocation, chapter.text());
            sources.add(new Source(
                    sourceId, pageUrl, retrievedAt, chapter.book() + " " + chapter.chapter(), textLocation));

            if (sources.size() % 100 == 0) {
                System.out.println("  downloaded " + sources.size() + " chapters");
            }
        }

        System.out.println("Downloaded " + sources.size() + " chapters, skipped " + skipped
                + " non-chapter pages");
        return CorpusDownloader.writeCatalog(outputDatastore, sourceCatalogId, sources);
    }

    /** Chapter and introduction page urls linked from the archive index, in index order. */
    private List<String> discoverPages() {
        Document index = Jsoup.parse(downloader.fetch(INDEX_URL), INDEX_URL);
        downloader.throttle();

        // The index wraps the whole Psalms link block in an html comment, so those anchors are
        // invisible to a normal parse. Re-parse comment bodies as markup to recover them.
        for (Element element : index.getAllElements()) {
            for (Node child : new ArrayList<>(element.childNodes())) {
                if (child instanceof Comment comment) {
                    child.replaceWith(Jsoup.parseBodyFragment(comment.getData(), INDEX_URL).body());
                }
            }
        }

        Map<String, String> discovered = new LinkedHashMap<>();
        for (Element anchor : index.select("a[href]")) {
            String absolute = anchor.absUrl("href");
            if (absolute.isEmpty()) {
                continue;
            }
            String withoutFragment = absolute.replaceAll("[?#].*$", "");
            if (!withoutFragment.startsWith("https://www.vatican.va/")) {
                continue;
            }
            if (PAGE_LINK.matcher(java.net.URI.create(withoutFragment).getPath()).find()) {
                discovered.putIfAbsent(withoutFragment, "");
            }
        }
        return new ArrayList<>(discovered.keySet());
    }

    private record Chapter(String book, int chapter, String text) {}

    /**
     * A page paragraph in two forms. Detection has to read the citation numbers, output has to be
     * rid of them, so both are carried side by side rather than derived from one another.
     */
    private record Block(String detect, String prose) {}

    /** Returns the chapter this page holds, or null when it is an introduction or front matter. */
    private static Chapter parseChapter(Document document) {
        List<Block> blocks = toBlocks(document);
        String book = findBook(blocks);
        Integer chapter = findChapter(blocks, book);
        if (book == null || chapter == null) {
            return null;
        }

        List<Block> scripture = findScriptureSegment(blocks);
        if (scripture == null) {
            return null;
        }

        List<String> prose = new ArrayList<>();
        for (Block block : removeLeadingHeadings(scripture, book, chapter)) {
            //the standalone verse-number paragraphs have no prose left once their digits are gone
            if (!block.prose().isEmpty()) {
                prose.add(block.prose());
            }
        }

        String text = CorpusDownloader.strip(String.join("\n", prose));
        if (text.isEmpty()) {
            return null;
        }
        return new Chapter(book, chapter, text);
    }

    private static final Pattern NUMBER_ONLY = Pattern.compile("\\d{1,3}");

    /**
     * Fences an inline citation marker so the two forms can be built from one pass. A plain
     * deletion would not do: chapter detection recognises scripture by finding a run of ascending
     * verse numbers, and on many pages those numbers exist only as inline superscripts, so removing
     * them early makes the page unrecognisable and drops it from the catalog altogether.
     */
    private static final String MARKER = "\u0001";
    private static final Pattern FENCED_MARKER =
            Pattern.compile(MARKER + "[^" + MARKER + "]*" + MARKER);
    private static final Pattern MARKER_FENCE = Pattern.compile(MARKER);

    /** A verse number opening a paragraph, recognised by the line break that follows it. */
    private static final Pattern LEADING_VERSE_NUMBER = Pattern.compile("[ \\t]*(\\d{1,3})[ \\t]*\\r?\\n");

    /** Page text as paragraphs, with horizontal rules preserved as {@value #RULE} markers. */
    private static List<Block> toBlocks(Document document) {
        document.select("script, style, noscript, svg").forEach(Node::remove);
        //every citation marker in this archive is a superscript: verse references and footnotes alike
        for (Element superscript : document.select("sup")) {
            superscript.replaceWith(
                    new TextNode(" " + MARKER + superscript.text().trim() + MARKER + " "));
        }
        // Some verses put their number inside the text paragraph rather than in one of its own,
        // separated by a line break. That break is what distinguishes a marker from prose such as
        // "40 days", so the number is fenced here while the break is still there to see.
        for (Element paragraph : document.select("p")) {
            for (Node child : paragraph.childNodes()) {
                if (!(child instanceof TextNode textNode)) {
                    continue;
                }
                String raw = textNode.getWholeText();
                Matcher leading = LEADING_VERSE_NUMBER.matcher(raw);
                if (leading.lookingAt()) {
                    textNode.text(MARKER + leading.group(1) + MARKER + " " + raw.substring(leading.end()));
                }
                break;   //only the paragraph's first text run can carry its marker
            }
        }
        document.select("hr").forEach(rule -> rule.replaceWith(new Element("p").text(RULE)));

        List<Block> blocks = new ArrayList<>();
        for (String block : CorpusDownloader.blockTexts(
                document.body() != null ? document.body() : document)) {
            String detect = CorpusDownloader.normalizeWhitespace(
                    MARKER_FENCE.matcher(block).replaceAll(" "));
            String prose = CorpusDownloader.normalizeWhitespace(
                    FENCED_MARKER.matcher(block).replaceAll(" "));
            //a paragraph holding only a verse number contributes nothing to the prose
            if (NUMBER_ONLY.matcher(prose).matches()) {
                prose = "";
            }
            blocks.add(new Block(detect, prose));
        }
        return blocks;
    }

    private static String findBook(List<Block> blocks) {
        //header lines are more reliable than book references inside the notes
        for (Block block : blocks.subList(0, Math.min(40, blocks.size()))) {
            String book = canonicalBook(block.detect());
            if (book != null) {
                return book;
            }
        }
        return null;
    }

    private static String canonicalBook(String line) {
        String book = BOOK_BY_LOWERCASE.get(line.toLowerCase(Locale.ROOT));
        return book == null ? null : BOOK_ALIASES.getOrDefault(book, book);
    }

    private static Integer findChapter(List<Block> blocks, String book) {
        for (Block block : blocks.subList(0, Math.min(60, blocks.size()))) {
            Matcher chapter = CHAPTER_LABEL.matcher(block.detect());
            if (chapter.matches()) {
                return Integer.parseInt(chapter.group(1));
            }
            Matcher psalm = PSALM_LABEL.matcher(block.detect());
            if (psalm.matches()) {
                return Integer.parseInt(psalm.group(1));
            }
        }
        //single chapter letters carry no "Chapter 1" heading
        if (book != null && SINGLE_CHAPTER_BOOKS.contains(book) && startsAtVerseOne(blocks)) {
            return 1;
        }
        return null;
    }

    private static boolean startsAtVerseOne(List<Block> blocks) {
        for (Block block : blocks) {
            Matcher matcher = VERSE_LINE.matcher(block.detect());
            if (matcher.matches() && Integer.parseInt(matcher.group(1)) == 1) {
                return true;
            }
        }
        return false;
    }

    /** The first rule-delimited segment that reads as scripture rather than navigation or notes. */
    private static List<Block> findScriptureSegment(List<Block> blocks) {
        int start = 0;
        for (int index = 0; index < blocks.size(); index++) {
            if (blocks.get(index).detect().toLowerCase(Locale.ROOT).contains("links to concordance")) {
                start = index + 1;
                break;
            }
        }

        for (List<Block> segment : splitOnRules(blocks.subList(start, blocks.size()))) {
            List<Block> cleaned = new ArrayList<>();
            for (Block block : segment) {
                if (!NAVIGATION_PHRASES.contains(block.detect().toLowerCase(Locale.ROOT))) {
                    cleaned.add(block);
                }
            }
            if (cleaned.isEmpty() || isNavigation(cleaned)) {
                continue;
            }
            if (looksLikeScripture(cleaned)) {
                return cleaned;
            }
        }
        return null;
    }

    private static List<List<Block>> splitOnRules(List<Block> blocks) {
        List<List<Block>> segments = new ArrayList<>();
        List<Block> current = new ArrayList<>();
        for (Block block : blocks) {
            if (block.detect().equals(RULE)) {
                if (!current.isEmpty()) {
                    segments.add(current);
                    current = new ArrayList<>();
                }
                continue;
            }
            current.add(block);
        }
        if (!current.isEmpty()) {
            segments.add(current);
        }
        return segments;
    }

    private static boolean isNavigation(List<Block> segment) {
        StringBuilder joined = new StringBuilder();
        for (Block block : segment) {
            joined.append(block.detect()).append(' ');
        }
        String text = joined.toString().trim().toLowerCase(Locale.ROOT);
        if (text.contains("copyright \u00a9 libreria editrice vaticana")) {
            return true;
        }
        if (segment.size() <= 8 && text.contains("previous") && text.contains("next")) {
            return true;
        }
        return text.equals("help");
    }

    /** A chapter opens at verse 1 and then counts upward in small steps. */
    private static boolean looksLikeScripture(List<Block> segment) {
        int last = 0;
        int found = 0;
        for (Block block : segment) {
            Matcher matcher = VERSE_LINE.matcher(block.detect());
            if (!matcher.matches()) {
                continue;
            }
            int number = Integer.parseInt(matcher.group(1));
            if (number == 1 && found == 0) {
                last = 1;
                found = 1;
            } else if (found > 0 && number > last && number <= last + 5) {
                last = number;
                found++;
            }
            if (found >= 3) {
                return true;
            }
        }
        //very short single chapter letters are still scripture
        return found > 0 && segment.size() >= 4;
    }

    private static List<Block> removeLeadingHeadings(List<Block> blocks, String book, int chapter) {
        List<Block> result = new ArrayList<>(blocks);
        while (!result.isEmpty()) {
            String line = result.get(0).detect();
            String lowered = line.toLowerCase(Locale.ROOT);
            boolean isHeading = book.equals(canonicalBook(line))
                    || CHAPTER_LABEL.matcher(line).matches()
                    || PSALM_LABEL.matcher(line).matches()
                    || lowered.equals("new american bible")
                    || lowered.equals("intratext - text");
            if (!isHeading) {
                break;
            }
            result.remove(0);
        }
        return result;
    }

    private static String slugify(String value) {
        String slug = NON_SLUG.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        return slug.isEmpty() ? "unknown" : slug;
    }

}
