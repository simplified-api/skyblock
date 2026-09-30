package api.simplified.skyblock.wiki;

import api.simplified.skyblock.wiki.client.SkyBlockWiki;
import api.simplified.skyblock.wiki.client.WikiPacing;
import api.simplified.skyblock.wiki.client.WikiResponseGuard;
import api.simplified.skyblock.wiki.exception.WikiApiException;
import api.simplified.skyblock.wiki.exception.WikiErrorException;
import api.simplified.skyblock.wiki.exception.WikiException;
import api.simplified.skyblock.wiki.exception.WikiIncludeSizeException;
import api.simplified.skyblock.wiki.exception.WikiLimitReachedException;
import api.simplified.skyblock.wiki.exception.WikiMissingFooterException;
import api.simplified.skyblock.wiki.exception.WikiMissingPageException;
import api.simplified.skyblock.wiki.exception.WikiRedirectException;
import api.simplified.skyblock.wiki.request.BucketQuery;
import api.simplified.skyblock.wiki.request.DplQuery;
import api.simplified.skyblock.wiki.request.WikiRequest;
import api.simplified.skyblock.wiki.request.WikiText;
import api.simplified.skyblock.wiki.response.WikiBucket;
import api.simplified.skyblock.wiki.response.WikiParse;
import api.simplified.skyblock.wiki.response.WikiQueryResult;
import api.simplified.skyblock.wiki.response.WikiResponse;
import api.simplified.skyblock.wiki.response.WikiSearchResult;
import dev.simplified.client.Client;
import dev.simplified.client.exception.NotModifiedException;
import dev.simplified.client.exception.PreconditionFailedException;
import dev.simplified.client.exception.RateLimitException;
import dev.simplified.client.ratelimit.RateLimitConfig;
import dev.simplified.client.request.Contract;
import dev.simplified.client.route.Route;
import dev.simplified.collection.ConcurrentMap;
import feign.Param;
import feign.QueryMap;
import feign.RequestLine;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Feign contract for the Hypixel SkyBlock wiki, a MediaWiki site: rendered pages and page sources
 * under {@code /w/}, and the parse, query, search and Bucket modules of {@code api.php}.
 *
 * <p>
 * Every {@code api.php} request is a {@link WikiRequest}, the one definition of its shape: the typed
 * methods here build one and send its {@link WikiRequest#getParameters() parameter map}, and a caller
 * without this contract fetches the same request at its {@link WikiRequest#getUrl() URL}. The four
 * methods bound to {@code api.php} take the parameter map as it stands and check nothing; everything
 * else goes through {@link #parse(WikiRequest)}, {@link #query(WikiRequest)},
 * {@link #search(WikiRequest)} and {@link #bucket(WikiRequest)}.
 *
 * <p>
 * Where each check runs:
 * <ul>
 *     <li><b>An error status</b> is raised by the {@link Client} that {@link SkyBlockWiki#client()}
 *     builds, before any method here returns: {@link NotModifiedException} for a 3xx,
 *     {@link PreconditionFailedException} for a 412, {@link RateLimitException} for a 429,
 *     {@link WikiApiException} for any other status of 400 or above.</li>
 *     <li><b>A refusal answered with a status of 200</b> - MediaWiki's error object, or Bucket's error
 *     string - is raised as {@link WikiErrorException} by the four methods that take a
 *     {@link WikiRequest}, on every answer they read, through {@link WikiResponse#requireSuccess()}.
 *     The methods bound to {@code api.php} return it in {@link WikiResponse#getError()}.</li>
 *     <li><b>A composite that misses part of what it names</b> - a red link, a template the wiki
 *     left out over its post-expand include size, or a {@link DplQuery} result stopped at its count
 *     - is raised by {@link #parse(WikiRequest)} for every
 *     {@linkplain WikiRequest#isComposite() composite} request, and so by
 *     {@link #transclude(String...)}, {@link #moduleSources(String...)} and
 *     {@link #templateTable(DplQuery)}, through {@link WikiParse#requireComplete()}; so is a
 *     {@link DplQuery} result that wrote no filled footer, whose counts that check reads, through
 *     {@link WikiParse#requireTableRows(int)}. {@link #parseWikitext(String)} leaves the checks to
 *     its caller, whose wikitext can reach a missing page on purpose.</li>
 *     <li><b>A Bucket answer holding as many rows as its limit</b>, which may stop short of the rows
 *     the query matches, is raised as {@link WikiLimitReachedException} by
 *     {@link #bucket(WikiRequest)} and so by {@link #queryBucket(BucketQuery)}, through
 *     {@link WikiBucket#requireUnderLimit(int)}.</li>
 *     <li><b>A search matching more pages than the wiki serves hits for</b>,
 *     {@link WikiRequest#MAX_SEARCH_HITS}, whose walk would end short of them without saying so, is
 *     raised as {@link WikiLimitReachedException} on the first part that counts them by
 *     {@link #search(WikiRequest, WikiPacing)} and {@link #query(WikiRequest, WikiPacing)}, and so
 *     by {@link #search(String)} and {@link #searchContents(String)}, through
 *     {@link WikiSearchResult#requireReachable()} and {@link WikiQueryResult#requireReachable()}.</li>
 *     <li><b>A redirect's stub</b> read in place of a page's source is raised as
 *     {@link WikiRedirectException} by {@link #getWikitext(String)} and
 *     {@link #getModuleSource(String)}. {@link #getSource(String)} answers the stub as it stands.</li>
 * </ul>
 * {@link WikiResponseGuard} runs the same checks on an answer fetched without this contract.
 *
 * <p>
 * Rendered pages are answered from Cloudflare's cache when it holds them; {@code api.php} is never
 * cached there, and every request to it is rendered by the wiki's own servers. The two routes keep
 * separate rate limits for that reason: sixty {@code api.php} requests a minute, two hundred and
 * forty page requests. The client refuses a request over either with a {@link RateLimitException}
 * before sending it, rather than waiting, and a single request raises that refusal. A paged read
 * sends one request per part, and every part, the first among them, goes through a
 * {@link WikiPacing}, which waits for the limiter to let the part through and sends it again, so a
 * read started with the minute spent waits for the next one, and a read of more parts than the limit
 * leaves in the minute completes rather than losing the parts it has read.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/api.php">MediaWiki API on the Hypixel SkyBlock wiki</a>
 */
@Route(value = WikiRequest.HOST, rateLimit = @RateLimitConfig(limit = 60, window = 60))
public interface SkyBlockWikiContract extends Contract {

    /**
     * Fetches a page as the wiki renders it, the full HTML document.
     *
     * @param title the page title, in any spelling {@link WikiRequest#normalize(String)} accepts
     * @return the HTML
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429, a 404 for a
     *     page the wiki does not hold
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the page route's
     *     rate limit refuses it
     */
    @Route(value = WikiRequest.HOST + "/w", rateLimit = @RateLimitConfig(limit = 240, window = 60))
    @RequestLine("GET /{title}")
    @NotNull String getPage(@Param(value = "title", expander = TitleExpander.class) @NotNull String title) throws WikiApiException;

    /**
     * Fetches a page's source as the wiki stores it, without checking it: the source of a title
     * naming a redirect is the redirect's stub, {@code #REDIRECT [[Target]]}, or
     * {@code return require [[Module:Target]]} for a module stored as Lua.
     *
     * @param title the page title with its namespace, in any spelling
     *     {@link WikiRequest#normalize(String)} accepts
     * @return the source
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429, a 404 for a
     *     page the wiki does not hold
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the page route's
     *     rate limit refuses it
     */
    @Route(value = WikiRequest.HOST + "/w", rateLimit = @RateLimitConfig(limit = 240, window = 60))
    @RequestLine("GET /{title}?action=raw")
    @NotNull String getSource(@Param(value = "title", expander = TitleExpander.class) @NotNull String title) throws WikiApiException;

    /**
     * Fetches a page's source - the wikitext of an article or template, the Lua of a module - and
     * checks that it is the page's own rather than a redirect's stub.
     *
     * @param title the page title with its namespace, in any spelling
     *     {@link WikiRequest#normalize(String)} accepts
     * @return the source
     * @throws WikiRedirectException if the title names a redirect, whose target the exception names
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429, a 404 for a
     *     page the wiki does not hold
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the page route's
     *     rate limit refuses it
     * @see WikiRequest#getWikitext(String)
     */
    default @NotNull String getWikitext(@NotNull String title) throws WikiApiException, WikiRedirectException {
        return WikiText.requireSource(title, this.getSource(title));
    }

    /**
     * Fetches a module's Lua source and checks that it is the module's own rather than a redirect's
     * stub - {@code return require [[Module:Target]]}, or the {@code #REDIRECT} of a module stored
     * as wikitext.
     *
     * @param module the module name, without the {@code Module:} namespace
     * @return the source
     * @throws WikiRedirectException if the module is a redirect, whose target the exception names
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429, a 404 for a
     *     module the wiki does not hold
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the page route's
     *     rate limit refuses it
     * @see WikiRequest#getModuleSource(String)
     */
    default @NotNull String getModuleSource(@NotNull String module) throws WikiApiException, WikiRedirectException {
        return this.getWikitext(WikiRequest.getModuleSource(module).getTitle());
    }

    /**
     * Sends a parse, {@code action=parse}, and binds its answer without checking it.
     *
     * @param parameters the parameter map of a {@link WikiRequest.Kind#PARSE} request
     * @return the answer, carrying a refusal in {@link WikiResponse#getError()} rather than raising it
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     */
    @RequestLine("GET /api.php")
    @NotNull WikiParse getParse(@QueryMap @NotNull Map<String, String> parameters) throws WikiApiException;

    /**
     * Sends one part of a read of page sources, {@code action=query&prop=revisions}, and binds its
     * answer without checking it or reading the parts after it.
     *
     * @param parameters the parameter map of a {@link WikiRequest.Kind#QUERY} request
     * @return the answer, carrying a refusal in {@link WikiResponse#getError()} rather than raising it
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     */
    @RequestLine("GET /api.php")
    @NotNull WikiQueryResult getQuery(@QueryMap @NotNull Map<String, String> parameters) throws WikiApiException;

    /**
     * Sends one part of a search, {@code action=query&list=search}, and binds its answer without
     * checking it or reading the parts after it.
     *
     * @param parameters the parameter map of a {@link WikiRequest.Kind#SEARCH} request
     * @return the answer, carrying a refusal in {@link WikiResponse#getError()} rather than raising it
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     */
    @RequestLine("GET /api.php")
    @NotNull WikiSearchResult getSearch(@QueryMap @NotNull Map<String, String> parameters) throws WikiApiException;

    /**
     * Sends a Bucket query, {@code action=bucket}, and binds its answer without checking it.
     *
     * @param parameters the parameter map of a {@link WikiRequest.Kind#BUCKET} request
     * @return the answer, carrying a refusal in {@link WikiResponse#getError()} rather than raising it
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     */
    @RequestLine("GET /api.php")
    @NotNull WikiBucket getBucket(@QueryMap @NotNull Map<String, String> parameters) throws WikiApiException;

    /**
     * Sends a parse and checks that the wiki answered it, and, for a
     * {@linkplain WikiRequest#isComposite() composite}, that the answer holds everything the
     * composite names and a filled footer for each {@link DplQuery} its wikitext renders.
     *
     * @param request the parse
     * @return the answer
     * @throws IllegalArgumentException if the request is not a {@link WikiRequest.Kind#PARSE} request
     * @throws WikiErrorException if the wiki refused the request
     * @throws WikiMissingPageException if the request is a composite reaching a document the wiki
     *     does not hold
     * @throws WikiIncludeSizeException if the request is a composite the wiki left part of out
     * @throws WikiMissingFooterException if the request is a composite holding a {@link DplQuery}
     *     whose result wrote no footer, or one without both counts
     * @throws WikiLimitReachedException if the request is a composite holding a {@link DplQuery}
     *     result that stops short of the pages its query matches
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     */
    default @NotNull WikiParse parse(@NotNull WikiRequest request) throws WikiApiException, WikiException {
        WikiParse parse = answered(this.getParse(request.require(WikiRequest.Kind.PARSE).getParameters()));

        if (!request.isComposite())
            return parse;

        return parse.requireComplete().requireTableRows(DplQuery.countIn(request.parameter("text").orElse("")));
    }

    /**
     * Reads page sources through every part of the answer and merges them, checking that the wiki
     * answered each part and waiting out the rate limit before each part as
     * {@link WikiPacing#DEFAULT} does.
     *
     * @param request the read
     * @return the merged answer, one entry per page and no continuation
     * @throws IllegalArgumentException if the request is not a {@link WikiRequest.Kind#QUERY} request,
     *     or a continuation renders it longer than {@link WikiRequest#MAX_URL_LENGTH}, which fails the
     *     whole read
     * @throws IllegalStateException if the wiki answers a continuation it answered before
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiLimitReachedException if the read is of the pages a search generates and a part
     *     counts more of them than {@link WikiRequest#MAX_SEARCH_HITS}
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the {@code api.php} rate limit refuses a
     *     part for longer than {@link WikiPacing#DEFAULT_MAX_WAIT}; either fails the whole read
     * @see #query(WikiRequest, WikiPacing)
     */
    default @NotNull WikiQueryResult query(@NotNull WikiRequest request) throws WikiApiException, WikiException {
        return this.query(request, WikiPacing.DEFAULT);
    }

    /**
     * Reads page sources through every part of the answer and merges them, checking that the wiki
     * answered each part and sending every part, the first among them, through a pacing.
     *
     * @param request the read
     * @param pacing how a part the rate limit refuses waits before it is sent again
     * @return the merged answer, one entry per page and no continuation
     * @throws IllegalArgumentException if the request is not a {@link WikiRequest.Kind#QUERY} request,
     *     or a continuation renders it longer than {@link WikiRequest#MAX_URL_LENGTH}, which fails the
     *     whole read
     * @throws IllegalStateException if the wiki answers a continuation it answered before
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiLimitReachedException if the read is of the pages a search generates and a part
     *     counts more of them than {@link WikiRequest#MAX_SEARCH_HITS}
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the pacing gives up on a part the
     *     {@code api.php} rate limit refuses; either fails the whole read
     */
    default @NotNull WikiQueryResult query(@NotNull WikiRequest request, @NotNull WikiPacing pacing) throws WikiApiException, WikiException {
        Map<String, String> first = request.require(WikiRequest.Kind.QUERY).getParameters();
        WikiQueryResult result = answered(pacing.send(() -> this.getQuery(first))).requireReachable();
        Set<Map<String, String>> followed = new HashSet<>();
        ConcurrentMap<String, String> continuation = result.getContinuation();

        while (!continuation.isEmpty()) {
            requireUnfollowed(followed, continuation);
            Map<String, String> next = request.continuing(continuation).getParameters();
            result.absorb(answered(pacing.send(() -> this.getQuery(next))).requireReachable());
            continuation = result.getContinuation();
        }

        return result;
    }

    /**
     * Searches through every part of the answer and merges the hits, checking that the wiki answered
     * each part and waiting out the rate limit before each part as {@link WikiPacing#DEFAULT} does.
     *
     * @param request the search
     * @return the merged answer, every hit in order and no continuation
     * @throws IllegalArgumentException if the request is not a {@link WikiRequest.Kind#SEARCH} request,
     *     or a continuation renders it longer than {@link WikiRequest#MAX_URL_LENGTH}, which fails the
     *     whole read
     * @throws IllegalStateException if the wiki answers a continuation it answered before
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiLimitReachedException if a part counts more matching pages than
     *     {@link WikiRequest#MAX_SEARCH_HITS}, the most hits the wiki serves
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the {@code api.php} rate limit refuses a
     *     part for longer than {@link WikiPacing#DEFAULT_MAX_WAIT}; either fails the whole read
     * @see #search(WikiRequest, WikiPacing)
     */
    default @NotNull WikiSearchResult search(@NotNull WikiRequest request) throws WikiApiException, WikiException {
        return this.search(request, WikiPacing.DEFAULT);
    }

    /**
     * Searches through every part of the answer and merges the hits, checking that the wiki answered
     * each part and sending every part, the first among them, through a pacing.
     *
     * @param request the search
     * @param pacing how a part the rate limit refuses waits before it is sent again
     * @return the merged answer, every hit in order and no continuation
     * @throws IllegalArgumentException if the request is not a {@link WikiRequest.Kind#SEARCH} request,
     *     or a continuation renders it longer than {@link WikiRequest#MAX_URL_LENGTH}, which fails the
     *     whole read
     * @throws IllegalStateException if the wiki answers a continuation it answered before
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiLimitReachedException if a part counts more matching pages than
     *     {@link WikiRequest#MAX_SEARCH_HITS}, the most hits the wiki serves
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the pacing gives up on a part the
     *     {@code api.php} rate limit refuses; either fails the whole read
     */
    default @NotNull WikiSearchResult search(@NotNull WikiRequest request, @NotNull WikiPacing pacing) throws WikiApiException, WikiException {
        Map<String, String> first = request.require(WikiRequest.Kind.SEARCH).getParameters();
        WikiSearchResult result = answered(pacing.send(() -> this.getSearch(first))).requireReachable();
        Set<Map<String, String>> followed = new HashSet<>();
        ConcurrentMap<String, String> continuation = result.getContinuation();

        while (!continuation.isEmpty()) {
            requireUnfollowed(followed, continuation);
            Map<String, String> next = request.continuing(continuation).getParameters();
            result.absorb(answered(pacing.send(() -> this.getSearch(next))).requireReachable());
            continuation = result.getContinuation();
        }

        return result;
    }

    /**
     * Runs a Bucket query and checks that the wiki answered it with fewer rows than the query's
     * limit, the limit its {@code query} parameter renders.
     *
     * @param request the query
     * @return the answer
     * @throws IllegalArgumentException if the request is not a {@link WikiRequest.Kind#BUCKET} request
     * @throws WikiErrorException if the wiki refused the query, as it refuses one naming a bucket it
     *     does not hold
     * @throws WikiLimitReachedException if the answer holds as many rows as the query's limit
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     */
    default @NotNull WikiBucket bucket(@NotNull WikiRequest request) throws WikiApiException, WikiException {
        WikiBucket bucket = answered(this.getBucket(request.require(WikiRequest.Kind.BUCKET).getParameters()));
        return bucket.requireUnderLimit(BucketQuery.limitOf(request.parameter("query").orElse("")));
    }

    /**
     * Parses one page, answering the props named.
     *
     * @param page the page title, in any spelling {@link WikiRequest#normalize(String)} accepts
     * @param props what the parse answers, {@code text} alone when none is named
     * @return the parse
     * @throws WikiErrorException if the wiki refused the parse, as it refuses one of a page it does
     *     not hold with {@code missingtitle}
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     * @see WikiRequest#parsePage(String, String...)
     */
    default @NotNull WikiParse parsePage(@NotNull String page, @NotNull String @NotNull ... props) throws WikiApiException, WikiErrorException {
        return this.parse(WikiRequest.parsePage(page, props));
    }

    /**
     * Parses wikitext that belongs to no page, answering its HTML and every document it reaches.
     *
     * <p>
     * Nothing here checks that the documents it reaches exist; a caller composing its own composite
     * calls {@link WikiParse#requireComplete()} on the answer.
     *
     * @param wikitext the wikitext to render
     * @return the parse
     * @throws IllegalArgumentException if the request renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiErrorException if the wiki refused the parse
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     * @see WikiRequest#parseWikitext(String)
     */
    default @NotNull WikiParse parseWikitext(@NotNull String wikitext) throws WikiApiException, WikiErrorException {
        return this.parse(WikiRequest.parseWikitext(wikitext));
    }

    /**
     * Renders several pages into one document and checks that it holds all of them.
     *
     * @param titles the pages to render, in order
     * @return the parse
     * @throws IllegalArgumentException if no page is named, or the request renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiMissingPageException if a page, or a document a page reaches, does not exist
     * @throws WikiIncludeSizeException if the wiki left part of the document out
     * @throws WikiErrorException if the wiki refused the parse
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     * @see WikiRequest#transclude(String...)
     */
    default @NotNull WikiParse transclude(@NotNull String @NotNull ... titles) throws WikiApiException, WikiException {
        return this.parse(WikiRequest.transclude(titles));
    }

    /**
     * Renders the Lua source of several modules into one document, each in its own {@code <pre>}
     * block, and checks that it holds all of them.
     *
     * @param modules the module names, without the {@code Module:} namespace
     * @return the parse
     * @throws IllegalArgumentException if no module is named, or the request renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiMissingPageException if a module does not exist
     * @throws WikiIncludeSizeException if the wiki left part of the document out
     * @throws WikiErrorException if the wiki refused the parse
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     * @see WikiRequest#moduleSources(String...)
     */
    default @NotNull WikiParse moduleSources(@NotNull String @NotNull ... modules) throws WikiApiException, WikiException {
        return this.parse(WikiRequest.moduleSources(modules));
    }

    /**
     * Renders one DynamicPageList query and checks that the document holds everything it reaches,
     * every page the query matches among it.
     *
     * <p>
     * A query over a template the wiki does not hold matches no page, as one over a template no page
     * uses does; the first is raised as a red link and the second answered as a result of no rows.
     *
     * @param query the query
     * @return the parse
     * @throws IllegalArgumentException if the request renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiMissingPageException if the template the query selects pages by, or a document the
     *     query reaches, does not exist
     * @throws WikiIncludeSizeException if the wiki left part of the document out
     * @throws WikiMissingFooterException if the result wrote no footer counting its pages, or one
     *     without both counts
     * @throws WikiLimitReachedException if the result holds fewer pages than the query matches,
     *     having stopped at the query's count or at {@link DplQuery#MAX_COUNT}
     * @throws WikiErrorException if the wiki refused the parse
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     * @see WikiRequest#templateTable(DplQuery)
     */
    default @NotNull WikiParse templateTable(@NotNull DplQuery query) throws WikiApiException, WikiException {
        return this.parse(WikiRequest.templateTable(query));
    }

    /**
     * Reads the source of up to {@link WikiRequest#MAX_TITLES} named pages.
     *
     * <p>
     * A title the wiki holds no page under is answered as a page that {@link WikiQueryResult.Page#isMissing()
     * is missing}, and a title no page can have as one that {@link WikiQueryResult.Page#isInvalid() is
     * invalid}; neither is raised.
     *
     * @param titles the page titles, each in any spelling {@link WikiRequest#normalize(String)} accepts
     * @return every page, merged across every part of the answer
     * @throws IllegalArgumentException if no title or more than {@link WikiRequest#MAX_TITLES} are
     *     named, or the request or a continuation of it renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the {@code api.php} rate limit refuses a
     *     part for longer than {@link WikiPacing#DEFAULT_MAX_WAIT}; either fails the whole read
     * @see WikiRequest#getContents(String...)
     */
    default @NotNull WikiQueryResult getContents(@NotNull String @NotNull ... titles) throws WikiApiException, WikiErrorException {
        return this.query(WikiRequest.getContents(titles));
    }

    /**
     * Reads the source of every page in one namespace whose title opens with a prefix, leaving out
     * every redirect under it.
     *
     * @param prefix the title prefix, in any spelling {@link WikiRequest#normalize(String)} accepts
     * @param namespace the namespace number, {@code 0} for articles
     * @return every page, merged across every part of the answer
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the {@code api.php} rate limit refuses a
     *     part for longer than {@link WikiPacing#DEFAULT_MAX_WAIT}; either fails the whole read
     * @see WikiRequest#getContentsByPrefix(String, int)
     */
    default @NotNull WikiQueryResult getContentsByPrefix(@NotNull String prefix, int namespace) throws WikiApiException, WikiErrorException {
        return this.query(WikiRequest.getContentsByPrefix(prefix, namespace));
    }

    /**
     * Reads the source of every page a search finds.
     *
     * @param query the search, in the wiki's search syntax
     * @return every page found, merged across every part of the answer
     * @throws IllegalArgumentException if the request or a continuation of it renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiLimitReachedException if the search matches more pages than
     *     {@link WikiRequest#MAX_SEARCH_HITS}, the most hits the wiki serves
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the {@code api.php} rate limit refuses a
     *     part for longer than {@link WikiPacing#DEFAULT_MAX_WAIT}; either fails the whole read
     * @see WikiRequest#searchContents(String)
     */
    default @NotNull WikiQueryResult searchContents(@NotNull String query) throws WikiApiException, WikiException {
        return this.query(WikiRequest.searchContents(query));
    }

    /**
     * Reads the source of every page, in any namespace, that transcludes a template.
     *
     * @param template the template name, without the {@code Template:} namespace
     * @return every page, merged across every part of the answer
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the {@code api.php} rate limit refuses a
     *     part for longer than {@link WikiPacing#DEFAULT_MAX_WAIT}; either fails the whole read
     * @see WikiRequest#getContentsUsingTemplate(String)
     */
    default @NotNull WikiQueryResult getContentsUsingTemplate(@NotNull String template) throws WikiApiException, WikiErrorException {
        return this.query(WikiRequest.getContentsUsingTemplate(template));
    }

    /**
     * Searches the wiki, answering each page found with a snippet of its source.
     *
     * @param query the search, in the wiki's search syntax
     * @return every hit, merged across every part of the answer
     * @throws IllegalArgumentException if the request or a continuation of it renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiErrorException if the wiki refused any part
     * @throws WikiLimitReachedException if the search matches more pages than
     *     {@link WikiRequest#MAX_SEARCH_HITS}, the most hits the wiki serves
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or when the {@code api.php} rate limit refuses a
     *     part for longer than {@link WikiPacing#DEFAULT_MAX_WAIT}; either fails the whole read
     * @see WikiRequest#search(String)
     */
    default @NotNull WikiSearchResult search(@NotNull String query) throws WikiApiException, WikiException {
        return this.search(WikiRequest.search(query));
    }

    /**
     * Runs a Bucket query and checks that it answered fewer rows than its limit.
     *
     * @param query the query, {@link BucketQuery#DEFAULT_LIMIT} rows when it names no limit
     * @return the rows
     * @throws IllegalArgumentException if the request renders longer than
     *     {@link WikiRequest#MAX_URL_LENGTH}
     * @throws WikiErrorException if the wiki refused the query, as it refuses one naming a bucket it
     *     does not hold
     * @throws WikiLimitReachedException if the answer holds as many rows as the query's limit, so it
     *     may stop short of the rows the query matches
     * @throws WikiApiException on an error status other than a 3xx, a 412 or a 429
     * @throws NotModifiedException on a 3xx status
     * @throws RateLimitException on a 429 status, or before the request is sent when the
     *     {@code api.php} rate limit refuses it
     * @see WikiRequest#queryBucket(BucketQuery)
     */
    default @NotNull WikiBucket queryBucket(@NotNull BucketQuery query) throws WikiApiException, WikiException {
        return this.bucket(WikiRequest.queryBucket(query));
    }

    /**
     * Checks that the wiki answered a request rather than refusing it.
     *
     * @param response the answer
     * @param <T> the answer type
     * @return the answer
     * @throws WikiErrorException if the answer carries a refusal
     */
    private static <T extends WikiResponse> @NotNull T answered(@NotNull T response) throws WikiErrorException {
        response.requireSuccess();
        return response;
    }

    /**
     * Records a continuation before the part it asks for is sent, so a walk through the parts ends
     * however the wiki repeats itself - at once or after other parts.
     *
     * @param followed the continuations the walk has already followed, which this one joins
     * @param continuation the continuation about to be followed
     * @throws IllegalStateException if the walk has already followed the continuation
     */
    private static void requireUnfollowed(@NotNull Set<Map<String, String>> followed, @NotNull Map<String, String> continuation) {
        if (!followed.add(Map.copyOf(continuation)))
            throw new IllegalStateException(String.format("The wiki answered the continuation '%s' a second time", continuation));
    }

    /**
     * Expands a {@code /w/} title into the form the path carries: normalized by
     * {@link WikiRequest#normalize(String)}, and left for the client to percent-encode.
     *
     * <p>
     * The client encodes the expanded title everywhere but {@code /}, so a title reaches the wiki
     * encoded once whatever it holds; a title encoded beforehand would be encoded a second time
     * wherever it holds a {@code /} or a {@code :} beside an escape.
     */
    final class TitleExpander implements Param.Expander {

        /** {@inheritDoc} */
        @Override
        public @NotNull String expand(@NotNull Object value) {
            return WikiRequest.normalize(String.valueOf(value));
        }

    }

}
