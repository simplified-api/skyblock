package api.simplified.skyblock.wiki.request;

import api.simplified.skyblock.wiki.SkyBlockWikiContract;
import api.simplified.skyblock.wiki.response.WikiParse;
import api.simplified.skyblock.wiki.response.WikiQueryResult;
import api.simplified.skyblock.wiki.response.WikiSearchResult;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentLinkedMap;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * One request to the Hypixel SkyBlock wiki, defined once and rendered two ways.
 *
 * <p>
 * Every shape {@link SkyBlockWikiContract} sends is built here, by a static factory named after the
 * contract method that sends it. A request renders as the ordered {@link #getParameters() parameter
 * map} the contract hands to {@code @QueryMap}, and as the {@link #getUrl() full URL} a caller
 * without the contract fetches, such as a dataflow pipeline. The two spell some characters
 * differently and name the same request: the wiki decodes every parameter of either to the same
 * value.
 *
 * <p>
 * A {@link #isComposite() composite} - several documents rendered into one parse - always answers
 * {@code text} and {@code templates}, the two props {@link WikiParse#requireComplete()} reads, so the
 * contract checks every composite it sends whichever method sends it.
 *
 * <p>
 * Every shape that names a page by title and can follow a redirect carries {@code redirects=1}: a
 * parse of a page and every read of page sources but a prefix read, which answer a title naming a
 * redirect with the page it leads to. A prefix read cannot follow one - the wiki refuses
 * {@code redirects=1} on the {@code allpages} generator - and leaves every redirect under the
 * prefix out instead. A search, a parse of wikitext and a Bucket query name no title to follow, a
 * rendered page follows a redirect without being asked, and a page source read raw cannot follow
 * one, so the contract refuses the redirect's stub instead of answering it as the page's source.
 *
 * <p>
 * The URL keeps each parameter as readable as the wiki takes it:
 * <ul>
 *     <li><b>Titles</b> - a page title, a title prefix and a list of titles - are normalized once, by
 *     {@link #normalize(String)}, and percent-encoded everywhere except {@code /} and {@code :}, the
 *     characters a MediaWiki title keeps readable. The same encoding builds the {@code /w/} path.</li>
 *     <li><b>Free text</b> - wikitext and search queries - is percent-encoded everywhere outside the
 *     RFC 3986 unreserved characters, a space as {@code %20}.</li>
 *     <li><b>A Bucket query</b> keeps {@code '}, {@code (}, {@code )} and {@code ,} readable, so it
 *     reads as the call chain it is.</li>
 * </ul>
 * A multi-valued parameter joins its values with {@code |}, which is encoded as {@code %7C}.
 *
 * <p>
 * The parameter map percent-encodes every character outside the unreserved set, in every value.
 * Feign's query map passes a value made only of unreserved characters and percent escapes through as
 * it stands and encodes any other value in full, so a readable value that carried an escape - a title
 * holding both a {@code /} and an apostrophe - would reach the wiki encoded twice.
 *
 * <p>
 * Every request is a {@code GET}, and none renders longer than {@link #MAX_URL_LENGTH}: a factory
 * whose request would outgrow it throws {@link IllegalArgumentException}. The length is measured over
 * the URL as the contract sends it, which is never shorter than {@link #getUrl()}.
 */
@Getter
public final class WikiRequest {

    /**
     * The host every request is sent to.
     */
    public static final @NotNull String HOST = "hypixelskyblock.minecraft.wiki";

    /**
     * The scheme and host every URL opens with.
     */
    public static final @NotNull String BASE_URL = "https://" + HOST;

    /**
     * The longest URL a request renders, in characters.
     *
     * <p>
     * The wiki's origin answers {@code 414 Request-URI Too Large} once a request line outgrows its
     * eight-kilobyte header buffer: a URL of 8,000 characters is answered, one of 8,300 is refused.
     */
    public static final int MAX_URL_LENGTH = 8000;

    /**
     * The most titles one {@code titles} query names, the cap MediaWiki sets for a client without
     * the bot right.
     */
    public static final int MAX_TITLES = 50;

    /**
     * The most hits one search reaches across all its parts, however many pages it matches.
     *
     * <p>
     * The wiki's CirrusSearch serves no hit at an offset of 10,000 or more, and answers the part that
     * reaches that offset with no continuation, so a walk through the parts of a larger search ends
     * as though it had read every hit: a search counting 40,812 pages, asked for five hits at offset
     * 9,998, answered two and nothing to continue with, as a list and as a generator alike.
     */
    public static final int MAX_SEARCH_HITS = 10_000;

    /**
     * The path every {@code api.php} request is sent to.
     */
    public static final @NotNull String API_PATH = "/api.php";

    /**
     * The path every rendered page and page source is read under, the title after it.
     */
    public static final @NotNull String PAGE_PATH = "/w/";

    private static final @NotNull List<String> COMPOSITE_PROPS = List.of("text", "templates");
    private static final char @NotNull [] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * The shape of the request, which names the contract method that sends it.
     */
    private final @NotNull Kind kind;

    /**
     * Whether the request is a parse rendering several documents into one, built by
     * {@link #transclude(String...)}, {@link #moduleSources(String...)} or
     * {@link #templateTable(DplQuery)}, whose answer the contract checks holds everything it names.
     */
    private final boolean composite;

    /**
     * The normalized title a {@link Kind#PAGE} or {@link Kind#WIKITEXT} request reads, spaces as
     * underscores and nothing percent-encoded; empty for a request to {@code api.php}.
     */
    private final @NotNull String title;

    /**
     * The path the request is sent to, percent-encoded.
     */
    @Getter(AccessLevel.NONE)
    private final @NotNull String path;

    /**
     * The parameters in the order they are sent, each with the encoding its URL form takes.
     */
    @Getter(AccessLevel.NONE)
    private final @NotNull LinkedHashMap<String, Parameter> parameters;

    private WikiRequest(
        @NotNull Kind kind,
        boolean composite,
        @NotNull String title,
        @NotNull String path,
        @NotNull LinkedHashMap<String, Parameter> parameters
    ) {
        this.kind = kind;
        this.composite = composite;
        this.title = title;
        this.path = path;
        this.parameters = parameters;
        int length = this.render(true).length();

        if (length > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(String.format(
                "The request renders '%s' characters, over the '%s' the wiki accepts",
                length,
                MAX_URL_LENGTH
            ));
        }
    }

    /**
     * Builds the request for a page as the wiki renders it, {@code GET /w/<title>}.
     *
     * @param title the page title, in any spelling {@link #normalize(String)} accepts
     * @return the request
     */
    public static @NotNull WikiRequest getPage(@NotNull String title) {
        String normalized = normalize(title);
        return new WikiRequest(Kind.PAGE, false, normalized, PAGE_PATH + encode(normalized, Encoding.TITLE), new LinkedHashMap<>());
    }

    /**
     * Builds the request for a page's source, {@code GET /w/<title>?action=raw} - the wikitext of an
     * article or template, the Lua of a module.
     *
     * <p>
     * A raw read does not follow a redirect: the source of a title naming one is the redirect's stub,
     * {@code #REDIRECT [[Target]]} or a module's {@code return require [[Module:Target]]}, which
     * {@link SkyBlockWikiContract#getWikitext(String)} refuses.
     *
     * @param title the page title, in any spelling {@link #normalize(String)} accepts
     * @return the request
     */
    public static @NotNull WikiRequest getWikitext(@NotNull String title) {
        String normalized = normalize(title);
        LinkedHashMap<String, Parameter> parameters = new LinkedHashMap<>();
        parameters.put("action", new Parameter("raw", Encoding.TEXT));
        return new WikiRequest(Kind.WIKITEXT, false, normalized, PAGE_PATH + encode(normalized, Encoding.TITLE), parameters);
    }

    /**
     * Builds the request for a module's Lua source, the {@link #getWikitext(String) source} of
     * {@code Module:<module>}.
     *
     * @param module the module name, without the {@code Module:} namespace
     * @return the request
     */
    public static @NotNull WikiRequest getModuleSource(@NotNull String module) {
        return getWikitext(WikiText.MODULE_NAMESPACE + module);
    }

    /**
     * Builds the request parsing one page, {@code action=parse&page=<title>}.
     *
     * <p>
     * A title naming a redirect parses the page it leads to, and the answer names the redirect in
     * {@link WikiParse#getRedirects()}.
     *
     * @param page the page title, in any spelling {@link #normalize(String)} accepts
     * @param props what the parse answers - {@code text}, {@code templates}, {@code sections} and
     *     any other {@code prop} the parse API takes - with {@code text} alone when none is named
     * @return the request
     */
    public static @NotNull WikiRequest parsePage(@NotNull String page, @NotNull String @NotNull ... props) {
        return api(Kind.PARSE)
            .add("action", "parse", Encoding.TEXT)
            .add("page", normalize(page), Encoding.TITLE)
            .add("prop", props.length == 0 ? "text" : String.join("|", props), Encoding.TEXT)
            .add("disablelimitreport", "1", Encoding.TEXT)
            .add("redirects", "1", Encoding.TEXT)
            .add("format", "json", Encoding.TEXT)
            .add("formatversion", "2", Encoding.TEXT)
            .build();
    }

    /**
     * Builds the request parsing wikitext that belongs to no page.
     *
     * <p>
     * The parse answers {@code text} and {@code templates}, so every page, module and template the
     * wikitext reaches is reported with whether it exists. The wiki parses the text as a page of its
     * own choosing, and a {@code {{PAGENAME}}} inside it names that page.
     *
     * <p>
     * The request is not a {@link #isComposite() composite}: the contract checks that the wiki
     * answered it and leaves what the wikitext reaches to its caller.
     *
     * @param wikitext the wikitext to render
     * @return the request
     * @throws IllegalArgumentException if the request renders longer than {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest parseWikitext(@NotNull String wikitext) {
        return parse(wikitext, false);
    }

    /**
     * Builds the composite rendering several pages into one document, the
     * {@link WikiText#transclusion(String) transclusion} of each in the order given.
     *
     * @param titles the pages to render, as {@link WikiText#transclusion(String)} takes them
     * @return the request
     * @throws IllegalArgumentException if no page is named, or the request renders longer than
     *     {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest transclude(@NotNull String @NotNull ... titles) {
        return parse(WikiText.join(WikiText::transclusion, titles), true);
    }

    /**
     * Builds the composite rendering the Lua source of several modules into one document, each as a
     * {@link WikiText#moduleSource(String) preformatted block} in the order given.
     *
     * @param modules the module names, without the {@code Module:} namespace
     * @return the request
     * @throws IllegalArgumentException if no module is named, or the request renders longer than
     *     {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest moduleSources(@NotNull String @NotNull ... modules) {
        return parse(WikiText.join(WikiText::moduleSource, modules), true);
    }

    /**
     * Builds the composite rendering one DynamicPageList query.
     *
     * <p>
     * The query renders a footer counting the pages its result holds against the pages it matches,
     * which the wiki writes whether the result holds pages or none, and names its template among the
     * parse's {@code templates}. The contract checks that the answer holds the footer filled, through
     * {@link WikiParse#requireTableRows(int)}, and that the result is whole and its template held,
     * through {@link WikiParse#requireComplete()}.
     *
     * @param query the query
     * @return the request
     * @throws IllegalArgumentException if the request renders longer than {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest templateTable(@NotNull DplQuery query) {
        return parse(query.render(), true);
    }

    /**
     * Builds the request for the source of up to {@link #MAX_TITLES} named pages, {@code prop=revisions}
     * over {@code titles}.
     *
     * <p>
     * A title naming a redirect is answered with the page it leads to, under that page's title, and
     * the answer names the redirect in {@link WikiQueryResult#getRedirects()}. The generator reads
     * built by {@link #searchContents(String)} and {@link #getContentsUsingTemplate(String)} follow
     * redirects the same way; {@link #getContentsByPrefix(String, int)} leaves them out.
     *
     * @param titles the page titles, each in any spelling {@link #normalize(String)} accepts
     * @return the request
     * @throws IllegalArgumentException if no title or more than {@link #MAX_TITLES} are named, or the
     *     request renders longer than {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest getContents(@NotNull String @NotNull ... titles) {
        if (titles.length == 0 || titles.length > MAX_TITLES) {
            throw new IllegalArgumentException(String.format(
                "The request names '%s' titles, where one query takes between '1' and '%s'",
                titles.length,
                MAX_TITLES
            ));
        }

        StringJoiner joined = new StringJoiner("|");
        Arrays.stream(titles).map(WikiRequest::normalize).forEach(joined::add);

        return api(Kind.QUERY)
            .add("action", "query", Encoding.TEXT)
            .add("prop", "revisions", Encoding.TEXT)
            .add("rvprop", "content", Encoding.TEXT)
            .add("rvslots", "main", Encoding.TEXT)
            .add("titles", joined.toString(), Encoding.TITLE)
            .add("redirects", "1", Encoding.TEXT)
            .add("format", "json", Encoding.TEXT)
            .add("formatversion", "2", Encoding.TEXT)
            .build();
    }

    /**
     * Builds the request for the source of every page in one namespace whose title opens with a
     * prefix, {@code generator=allpages}.
     *
     * <p>
     * A redirect under the prefix is left out rather than followed, {@code gapfilterredir=nonredirects}:
     * the wiki refuses {@code redirects=1} on this generator with the error {@code params}, and
     * following one would answer a page whose title need not open with the prefix. The page a
     * redirect leads to is read in its own right when its title opens with the prefix.
     *
     * @param prefix the title prefix, in any spelling {@link #normalize(String)} accepts
     * @param namespace the namespace number, {@code 0} for articles
     * @return the request
     */
    public static @NotNull WikiRequest getContentsByPrefix(@NotNull String prefix, int namespace) {
        return api(Kind.QUERY)
            .add("action", "query", Encoding.TEXT)
            .add("generator", "allpages", Encoding.TEXT)
            .add("gapprefix", normalize(prefix), Encoding.TITLE)
            .add("gapnamespace", String.valueOf(namespace), Encoding.TEXT)
            .add("gaplimit", "max", Encoding.TEXT)
            .add("gapfilterredir", "nonredirects", Encoding.TEXT)
            .add("prop", "revisions", Encoding.TEXT)
            .add("rvprop", "content", Encoding.TEXT)
            .add("rvslots", "main", Encoding.TEXT)
            .add("format", "json", Encoding.TEXT)
            .add("formatversion", "2", Encoding.TEXT)
            .build();
    }

    /**
     * Builds the request for the source of every page a search finds, {@code generator=search}.
     *
     * <p>
     * Each part counts the pages the search matches, {@code gsrinfo=totalhits}, which the contract
     * checks against {@link #MAX_SEARCH_HITS} through {@link WikiQueryResult#requireReachable()}.
     *
     * @param query the search, in the wiki's search syntax - {@code insource:"type=[[Brews]]"} matches
     *     the source rather than the rendered text
     * @return the request
     * @throws IllegalArgumentException if the request renders longer than {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest searchContents(@NotNull String query) {
        return api(Kind.QUERY)
            .add("action", "query", Encoding.TEXT)
            .add("generator", "search", Encoding.TEXT)
            .add("gsrsearch", query, Encoding.TEXT)
            .add("gsrlimit", "50", Encoding.TEXT)
            .add("gsrinfo", "totalhits", Encoding.TEXT)
            .add("prop", "revisions", Encoding.TEXT)
            .add("rvprop", "content", Encoding.TEXT)
            .add("rvslots", "main", Encoding.TEXT)
            .add("redirects", "1", Encoding.TEXT)
            .add("format", "json", Encoding.TEXT)
            .add("formatversion", "2", Encoding.TEXT)
            .build();
    }

    /**
     * Builds the request for the source of every page, in any namespace, that transcludes a
     * template, {@code generator=embeddedin}.
     *
     * @param template the template name, without the {@code Template:} namespace
     * @return the request
     */
    public static @NotNull WikiRequest getContentsUsingTemplate(@NotNull String template) {
        return api(Kind.QUERY)
            .add("action", "query", Encoding.TEXT)
            .add("generator", "embeddedin", Encoding.TEXT)
            .add("geititle", normalize(WikiText.TEMPLATE_NAMESPACE + template), Encoding.TITLE)
            .add("geilimit", "50", Encoding.TEXT)
            .add("prop", "revisions", Encoding.TEXT)
            .add("rvprop", "content", Encoding.TEXT)
            .add("rvslots", "main", Encoding.TEXT)
            .add("redirects", "1", Encoding.TEXT)
            .add("format", "json", Encoding.TEXT)
            .add("formatversion", "2", Encoding.TEXT)
            .build();
    }

    /**
     * Builds the request for the pages a search finds, each with a snippet of its source around the
     * match, {@code list=search}.
     *
     * <p>
     * The answer is left in the API's default format version, which carries the hits exactly as
     * {@code formatversion=2} does. Each part counts the pages the search matches,
     * {@code srinfo=totalhits}, which the contract checks against {@link #MAX_SEARCH_HITS} through
     * {@link WikiSearchResult#requireReachable()}.
     *
     * @param query the search, in the wiki's search syntax - {@code insource:/regex/} matches a
     *     regular expression against the source
     * @return the request
     * @throws IllegalArgumentException if the request renders longer than {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest search(@NotNull String query) {
        return api(Kind.SEARCH)
            .add("action", "query", Encoding.TEXT)
            .add("list", "search", Encoding.TEXT)
            .add("srsearch", query, Encoding.TEXT)
            .add("srprop", "snippet", Encoding.TEXT)
            .add("srinfo", "totalhits", Encoding.TEXT)
            .add("srlimit", "500", Encoding.TEXT)
            .add("format", "json", Encoding.TEXT)
            .build();
    }

    /**
     * Builds the request running one Bucket query, {@code action=bucket}.
     *
     * <p>
     * The answer is left in the API's default format version with {@code utf8=1}, which carries the
     * rows exactly as {@code formatversion=2} does. The query renders its limit, which the contract
     * reads back out of the {@code query} parameter to check the answer against.
     *
     * @param query the query
     * @return the request
     * @throws IllegalArgumentException if the request renders longer than {@link #MAX_URL_LENGTH}
     */
    public static @NotNull WikiRequest queryBucket(@NotNull BucketQuery query) {
        return api(Kind.BUCKET)
            .add("action", "bucket", Encoding.TEXT)
            .add("query", query.render(), Encoding.BUCKET)
            .add("format", "json", Encoding.TEXT)
            .add("utf8", "1", Encoding.TEXT)
            .build();
    }

    /**
     * Derives the same parse request answering other props, each parameter kept where it stands.
     *
     * <p>
     * A {@link #isComposite() composite} keeps {@code text} and {@code templates} among its props:
     * without either, {@link WikiParse#requireComplete()} has nothing to read and passes a composite
     * that misses part of what it names.
     *
     * @param props what the parse answers, as {@link #parsePage(String, String...)} takes them
     * @return the derived request
     * @throws IllegalArgumentException if this is not a {@link Kind#PARSE} request, no prop is named,
     *     or this is a composite and the props leave out {@code text} or {@code templates}
     */
    public @NotNull WikiRequest withProps(@NotNull String @NotNull ... props) {
        this.require(Kind.PARSE);

        if (props.length == 0)
            throw new IllegalArgumentException("The parse names no prop");

        String joined = String.join("|", props);

        if (this.composite && !Arrays.asList(props).containsAll(COMPOSITE_PROPS)) {
            throw new IllegalArgumentException(String.format(
                "A composite answers 'text' and 'templates', which its check reads, not '%s'",
                joined
            ));
        }

        LinkedHashMap<String, Parameter> parameters = new LinkedHashMap<>(this.parameters);
        parameters.put("prop", new Parameter(joined, Encoding.TEXT));
        return new WikiRequest(this.kind, this.composite, this.title, this.path, parameters);
    }

    /**
     * Derives the request that continues this one where an answer left off.
     *
     * <p>
     * MediaWiki pages a long answer and names, in its {@code continue} object, the parameters that
     * ask for the next page. Each is set here: one this request already carries keeps its place and
     * takes the new value, and any other is appended.
     *
     * @param continuation the {@code continue} object of the answer to continue from
     * @return the derived request
     * @throws IllegalArgumentException if this is not a request to {@code api.php}, or the request
     *     renders longer than {@link #MAX_URL_LENGTH}
     */
    public @NotNull WikiRequest continuing(@NotNull Map<String, String> continuation) {
        if (this.kind == Kind.PAGE || this.kind == Kind.WIKITEXT)
            throw new IllegalArgumentException(String.format("A '%s' request does not continue", this.kind));

        LinkedHashMap<String, Parameter> parameters = new LinkedHashMap<>(this.parameters);
        continuation.forEach((name, value) -> parameters.put(name, new Parameter(value, Encoding.TEXT)));
        return new WikiRequest(this.kind, this.composite, this.title, this.path, parameters);
    }

    /**
     * Renders the parameters the contract hands to {@code @QueryMap}, in the order they are sent,
     * each value percent-encoded everywhere outside the RFC 3986 unreserved characters.
     *
     * @return the parameters, which cannot be modified
     */
    public @NotNull ConcurrentLinkedMap<String, String> getParameters() {
        LinkedHashMap<String, String> encoded = new LinkedHashMap<>();
        this.parameters.forEach((name, parameter) -> encoded.put(name, encode(parameter.value(), Encoding.TEXT)));
        return Concurrent.newUnmodifiableLinkedMap(encoded);
    }

    /**
     * Renders the full URL of the request, each parameter as readable as the wiki takes it.
     *
     * @return the URL
     */
    public @NotNull String getUrl() {
        return this.render(false);
    }

    /**
     * Asks that this request is of one kind.
     *
     * @param kind the kind the caller sends
     * @return this request
     * @throws IllegalArgumentException if this request is of another kind
     */
    public @NotNull WikiRequest require(@NotNull Kind kind) {
        if (this.kind != kind)
            throw new IllegalArgumentException(String.format("The request is a '%s' request, not a '%s' one", this.kind, kind));

        return this;
    }

    /**
     * Reads one parameter as the wiki reads it, nothing encoded.
     *
     * @param name the parameter name
     * @return the value, empty when the request does not carry the parameter
     */
    public @NotNull Optional<String> parameter(@NotNull String name) {
        return Optional.ofNullable(this.parameters.get(name)).map(Parameter::value);
    }

    /**
     * Renders the URL.
     *
     * @param strict whether the URL is encoded as the contract sends it - a {@code /w/} title
     *     everywhere but {@code /}, and every parameter outside the unreserved characters - rather
     *     than as readable as the wiki takes it
     * @return the URL
     */
    private @NotNull String render(boolean strict) {
        boolean page = this.kind == Kind.PAGE || this.kind == Kind.WIKITEXT;
        StringBuilder url = new StringBuilder(BASE_URL).append(strict && page ? PAGE_PATH + encode(this.title, Encoding.PATH) : this.path);
        char separator = '?';

        for (Map.Entry<String, Parameter> entry : this.parameters.entrySet()) {
            Parameter parameter = entry.getValue();
            url.append(separator)
                .append(entry.getKey())
                .append('=')
                .append(encode(parameter.value(), strict ? Encoding.TEXT : parameter.encoding()));
            separator = '&';
        }

        return url.toString();
    }

    /**
     * Normalizes a page title the way MediaWiki reads it: surrounding spaces and underscores are
     * dropped, and every run of them inside becomes one underscore.
     *
     * <p>
     * A title is otherwise taken as written, so a namespace is named in it ({@code Module:Pet/Data})
     * and nothing in it is percent-encoded.
     *
     * @param title the title in any spelling
     * @return the normalized title
     */
    public static @NotNull String normalize(@NotNull String title) {
        return title.replaceAll("[ _]+", "_").replaceAll("^_|_$", "");
    }

    /**
     * Percent-encodes a value as UTF-8, keeping the RFC 3986 unreserved characters and the ones the
     * encoding keeps readable.
     *
     * @param value the value
     * @param encoding the characters kept readable beyond the unreserved ones
     * @return the encoded value, escapes in upper-case hex
     */
    static @NotNull String encode(@NotNull String value, @NotNull Encoding encoding) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);

        for (byte b : bytes) {
            int c = b & 0xFF;

            if (isUnreserved(c) || encoding.keeps(c))
                encoded.append((char) c);
            else
                encoded.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
        }

        return encoded.toString();
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z')
            || (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9')
            || c == '-' || c == '.' || c == '_' || c == '~';
    }

    private static @NotNull Builder api(@NotNull Kind kind) {
        return new Builder(kind);
    }

    /**
     * Builds a parse of wikitext that belongs to no page, answering its HTML and every document it
     * reaches.
     *
     * @param wikitext the wikitext to render
     * @param composite whether the wikitext is a composite the contract checks
     * @return the request
     * @throws IllegalArgumentException if the request renders longer than {@link #MAX_URL_LENGTH}
     */
    private static @NotNull WikiRequest parse(@NotNull String wikitext, boolean composite) {
        return api(Kind.PARSE)
            .add("action", "parse", Encoding.TEXT)
            .add("contentmodel", "wikitext", Encoding.TEXT)
            .add("prop", String.join("|", COMPOSITE_PROPS), Encoding.TEXT)
            .add("disablelimitreport", "1", Encoding.TEXT)
            .add("format", "json", Encoding.TEXT)
            .add("formatversion", "2", Encoding.TEXT)
            .add("text", wikitext, Encoding.TEXT)
            .composite(composite)
            .build();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String toString() {
        return this.getUrl();
    }

    /**
     * The shape of a request, which names the contract method that sends it.
     */
    public enum Kind {

        /**
         * A rendered page, {@code GET /w/<title>}.
         */
        PAGE,

        /**
         * A page's source, {@code GET /w/<title>?action=raw}.
         */
        WIKITEXT,

        /**
         * A parse, {@code action=parse}.
         */
        PARSE,

        /**
         * A read of page sources, {@code action=query&prop=revisions}.
         */
        QUERY,

        /**
         * A search, {@code action=query&list=search}.
         */
        SEARCH,

        /**
         * A Bucket query, {@code action=bucket}.
         */
        BUCKET

    }

    /**
     * The characters a parameter keeps readable in {@link #getUrl()} beyond the unreserved ones.
     */
    enum Encoding {

        /**
         * A title or a list of titles, keeping {@code /} and {@code :}.
         */
        TITLE("/:"),

        /**
         * A {@code /w/} title as the contract sends it, keeping {@code /}.
         */
        PATH("/"),

        /**
         * Free text, keeping nothing beyond the unreserved characters.
         */
        TEXT(""),

        /**
         * A Bucket query, keeping the quotes, parentheses and commas of its call chain.
         */
        BUCKET("'(),");

        private final @NotNull String kept;

        Encoding(@NotNull String kept) {
            this.kept = kept;
        }

        private boolean keeps(int c) {
            return c < 0x80 && this.kept.indexOf(c) >= 0;
        }

    }

    /**
     * One parameter value, not encoded, with the encoding its URL form takes.
     *
     * @param value the value as the wiki reads it
     * @param encoding the encoding its readable form takes
     */
    private record Parameter(@NotNull String value, @NotNull Encoding encoding) {}

    /**
     * Collects the parameters of a request to {@code api.php} in the order they are sent.
     */
    private static final class Builder {

        private final @NotNull Kind kind;
        private final @NotNull LinkedHashMap<String, Parameter> parameters = new LinkedHashMap<>();
        private boolean composite;

        private Builder(@NotNull Kind kind) {
            this.kind = kind;
        }

        private @NotNull Builder add(@NotNull String name, @NotNull String value, @NotNull Encoding encoding) {
            this.parameters.put(name, new Parameter(value, encoding));
            return this;
        }

        private @NotNull Builder composite(boolean composite) {
            this.composite = composite;
            return this;
        }

        private @NotNull WikiRequest build() {
            return new WikiRequest(this.kind, this.composite, "", API_PATH, this.parameters);
        }

    }

}
