package com.mgaray.ragserver.sourcecatalogdownloader.bible;

import com.mgaray.ragserver.Models.Source;
import com.mgaray.ragserver.Models.SourceCatalog;
import com.mgaray.ragserver.ingest.SourceCatalogValidator;
import com.mgaray.ragserver.storage.data.IDatastore;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared plumbing for the corpus downloaders: polite HTTP fetching, the text normalization the
 * html-to-plain-text passes need, and writing a finished source catalog.
 *
 * Each corpus keeps its own program because the discovery and parsing rules differ substantially;
 * only the parts that are genuinely identical live here.
 */
public class CorpusDownloader {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RETRIES = 4;
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(408, 429, 500, 502, 503, 504);

    /** Matches the python downloaders' datetime.now(timezone.utc).isoformat(). */
    private static final DateTimeFormatter RETRIEVED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+00:00'");

    private final String userAgent;
    private final Duration throttle;
    private final HttpClient httpClient;

    public CorpusDownloader(String userAgent, Duration throttle) {
        this.userAgent = userAgent;
        this.throttle = throttle;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    public static String retrievedAtNow() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(RETRIEVED_AT);
    }

    //-----HTTP-------------------------------------------------------------------------------------

    /** Fetches a page, or returns null when the server answers 404/410 or the body is not html. */
    public String fetchOptional(String url) {
        Response response = send(url);
        if (response == null || response.statusCode() >= 400) {
            return null;
        }
        return response.body();
    }

    /** Fetches a page, throwing when it cannot be retrieved. */
    public String fetch(String url) {
        Response response = send(url);
        if (response == null) {
            throw new RuntimeException("Request failed for " + url);
        }
        if (response.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    public record Response(int statusCode, String body, String contentType) {}

    private Response send(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                sleep(Duration.ofSeconds(1L << (attempt - 1))); //exponential backoff: 1s, 2s, 4s, 8s
            }
            try {
                HttpResponse<byte[]> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (RETRYABLE_STATUS_CODES.contains(response.statusCode())) {
                    continue;
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                return new Response(response.statusCode(), decodeHtml(response.body()), contentType);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (java.io.IOException e) {
                //fall through to the next attempt
            }
        }
        return null;
    }

    /** Sleeps the configured inter-request delay. Call after each request to stay polite. */
    public void throttle() {
        sleep(throttle);
    }

    public static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    //-----Decoding---------------------------------------------------------------------------------

    /**
     * Decodes a page body, then repairs cp1252 mojibake.
     *
     * Charset declarations on these sites are not reliable (the Oregon Legislature pages announce
     * windows-1252 but serve utf-8), so the bytes are decoded strictly as utf-8 and only fall back
     * to windows-1252 when that fails - which is what the bytes actually are in both cases.
     */
    public static String decodeHtml(byte[] bytes) {
        String decoded = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (decoded == null) {
            decoded = new String(bytes, Charset.forName("windows-1252"));
        }
        return repairControlCharacters(decoded);
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
            return buffer.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /**
     * Maps the C1 block (U+0080-U+009F) onto the cp1252 characters those byte values stand for.
     *
     * Some publishers transcode cp1252 source into utf-8 by widening each byte rather than mapping
     * it, which turns curly quotes, dashes and bullets into unprintable control characters. The
     * Oregon Revised Statutes pages do this to roughly 78,000 characters, including nearly every
     * apostrophe. C1 controls carry no meaning in html text, so rewriting them is safe.
     */
    public static String repairControlCharacters(String value) {
        StringBuilder builder = null;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character >= '\u0080' && character <= '\u009F') {
                if (builder == null) {
                    builder = new StringBuilder(value.length()).append(value, 0, index);
                }
                builder.append(CP1252_C1[character - 0x80]);
            } else if (builder != null) {
                builder.append(character);
            }
        }
        return builder == null ? value : builder.toString();
    }

    // cp1252 values for bytes 0x80-0x9F. Positions unused by cp1252 keep their original code point.
    private static final char[] CP1252_C1 = {
            '\u20AC', '\u0081', '\u201A', '\u0192', '\u201E', '\u2026', '\u2020', '\u2021',
            '\u02C6', '\u2030', '\u0160', '\u2039', '\u0152', '\u008D', '\u017D', '\u008F',
            '\u0090', '\u2018', '\u2019', '\u201C', '\u201D', '\u2022', '\u2013', '\u2014',
            '\u02DC', '\u2122', '\u0161', '\u203A', '\u0153', '\u009D', '\u017E', '\u0178',
    };

    //-----Text extraction--------------------------------------------------------------------------

    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|[\\n\\r]");
    private static final Pattern HORIZONTAL_WHITESPACE = Pattern.compile("[ \\t\\u00a0]+");
    // UNICODE_CHARACTER_CLASS is required: java's bare \s is ascii only and would leave the
    // non-breaking spaces that html entities decode to sitting inside otherwise-collapsed runs.
    private static final Pattern ANY_WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Concatenates every descendant text node, mirroring BeautifulSoup's get_text(separator).
     * jsoup's own text() collapses a whole page into one line and wholeText() keeps the source
     * indentation, so neither is usable for building line-oriented corpus text.
     */
    public static String textNodes(Element element, String separator) {
        List<String> parts = new ArrayList<>();
        element.traverse((node, depth) -> {
            if (node instanceof TextNode textNode) {
                parts.add(textNode.getWholeText());
            }
        });
        return String.join(separator, parts);
    }

    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "td", "tr", "blockquote", "pre");

    /**
     * One entry per block element that holds text, with each entry's internal whitespace collapsed.
     *
     * Both bible sources hard-wrap their markup, so flattening text nodes on newlines splits
     * sentences mid-clause. Block boundaries are where the paragraphs actually are, so reading them
     * out recovers prose: a text run is closed whenever a block opens or closes.
     */
    public static List<String> blockTexts(Element root) {
        List<String> blocks = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        collectBlocks(root, blocks, pending);
        flushBlock(pending, blocks);
        return blocks;
    }

    private static void collectBlocks(Element element, List<String> blocks, StringBuilder pending) {
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                pending.append(textNode.getWholeText()).append(' ');
            } else if (child instanceof Element childElement) {
                if (BLOCK_TAGS.contains(childElement.tagName())) {
                    flushBlock(pending, blocks);   //close the run this block interrupts
                    collectBlocks(childElement, blocks, pending);
                    flushBlock(pending, blocks);   //the block's own text is a paragraph of its own
                } else {
                    collectBlocks(childElement, blocks, pending); //inline: keep accumulating
                }
            }
        }
    }

    private static void flushBlock(StringBuilder pending, List<String> blocks) {
        String text = normalizeWhitespace(pending.toString());
        pending.setLength(0);
        if (!text.isEmpty()) {
            blocks.add(text);
        }
    }

    /** Collapses every whitespace run, including line breaks, to a single space. */
    public static String normalizeWhitespace(String value) {
        return strip(ANY_WHITESPACE.matcher(value).replaceAll(" "));
    }

    /** Splits on line breaks, trims each line, and drops the blank ones. */
    public static List<String> toLines(String text, boolean collapseInnerWhitespace) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : LINE_BREAK.split(text, -1)) {
            String line = collapseInnerWhitespace
                    ? ANY_WHITESPACE.matcher(rawLine).replaceAll(" ")
                    : rawLine;
            line = strip(line);
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Collapses runs of spaces, tabs and non-breaking spaces, then trims. */
    public static String normalizeLine(String value) {
        return strip(HORIZONTAL_WHITESPACE.matcher(value).replaceAll(" "));
    }

    /**
     * Trims a line, treating the non-breaking spaces that &nbsp; and friends decode to as
     * whitespace. String.strip() does not: Character.isWhitespace excludes U+00A0/U+2007/U+202F,
     * which would leave lines holding nothing but a non-breaking space in the corpus.
     */
    public static String strip(String value) {
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

    //-----Catalog output---------------------------------------------------------------------------

    public static String sourceTextLocation(String sourceCatalogId, String sourceId) {
        return sourceCatalogId + "/sources/" + sourceId + ".txt";
    }

    /** Writes the catalog document alongside the already-written source texts and validates it. */
    public static List<String> writeCatalog(
            IDatastore outputDatastore, String sourceCatalogId, List<Source> sources) {
        SourceCatalog sourceCatalog = new SourceCatalog(sourceCatalogId, sources);
        outputDatastore.writeObject(sourceCatalogId + "/sourceCatalog.json", sourceCatalog);
        return SourceCatalogValidator.validate(sourceCatalog);
    }

}
