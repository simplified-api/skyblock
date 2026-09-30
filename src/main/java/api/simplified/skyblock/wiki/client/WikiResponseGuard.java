package api.simplified.skyblock.wiki.client;

import api.simplified.skyblock.wiki.SkyBlockWikiContract;
import api.simplified.skyblock.wiki.exception.WikiErrorException;
import api.simplified.skyblock.wiki.exception.WikiErrorResponse;
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
import api.simplified.skyblock.wiki.response.WikiResponse;
import api.simplified.skyblock.wiki.response.WikiSearchResult;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.simplified.annotations.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks an answer the wiki gave to a request sent without {@link SkyBlockWikiContract} - a dataflow
 * pipeline's fetch of a {@link WikiRequest#getUrl() request's URL} - with the checks the contract
 * runs on the same request.
 *
 * <p>
 * {@link #check(URI, String)} reads the request's shape from its URL and runs each check the answer
 * carries evidence for, through the method the contract runs it with:
 * <ul>
 *     <li><b>Every {@code api.php} request answered in JSON</b> - the refusal the wiki writes in
 *     place of a result, raised as {@link WikiErrorException} through
 *     {@link WikiResponse#requireSuccess()}.</li>
 *     <li><b>A parse of wikitext</b>, {@code action=parse} with {@code text} and
 *     {@code formatversion=2} - the composite checks of {@link WikiParse#requireComplete()}: a red
 *     link, read from {@code templates}, where the template a {@link DplQuery} selects pages by is
 *     named too; and a template left out at the post-expand include size and a {@link DplQuery}
 *     result stopped at its count or holding a footer without both counts, both read from
 *     {@code text}. A composite and loose wikitext answering the same props render the
 *     same URL, so loose wikitext is checked as a composite; wikitext that reaches a missing page on
 *     purpose asks for {@code text} alone.</li>
 *     <li><b>A parse of wikitext rendering a {@link DplQuery}</b>, recognised by the footer each
 *     query carries in the {@code text} parameter, and answering {@code text} - a result that wrote
 *     no filled footer, raised as {@link WikiMissingFooterException} through
 *     {@link WikiParse#requireTableRows(int)}, one filled footer asked of the answer for every query
 *     {@code text} renders.</li>
 *     <li><b>A search</b>, {@code list=search} or {@code generator=search}, whose answer counts the
 *     pages it matches - more of them than {@link WikiRequest#MAX_SEARCH_HITS}, the most hits the
 *     wiki serves, raised as {@link WikiLimitReachedException} through
 *     {@link WikiSearchResult#requireReachable()}. Every part a fetcher reads is checked, the first
 *     among them.</li>
 *     <li><b>A Bucket query</b>, {@code action=bucket} - an answer holding as many rows as the limit
 *     its {@code query} parameter runs at, raised as {@link WikiLimitReachedException} through
 *     {@link WikiBucket#requireUnderLimit(int)}. A query that renders no limit runs at
 *     {@link BucketQuery#DEFAULT_LIMIT}, and one over {@link BucketQuery#MAX_LIMIT} at that.</li>
 *     <li><b>A page source</b>, {@code /w/<title>?action=raw} or
 *     {@code /index.php?title=<title>&action=raw} - a redirect's stub, raised as
 *     {@link WikiRedirectException}.</li>
 * </ul>
 *
 * <p>
 * What a URL carries no evidence for is not checked:
 * <ul>
 *     <li>a red link in a parse that does not answer {@code templates}, and the include size and
 *     {@link DplQuery} results of one that does not answer {@code text};</li>
 *     <li>a DynamicPageList call the {@code text} parameter writes without {@link DplQuery}'s footer,
 *     whose result writes none;</li>
 *     <li>anything past the refusal in a parse of a page, {@code page=}, which the contract does not
 *     check either, and in a read of page sources no search generates, whose continuation is the
 *     fetcher's to follow;</li>
 *     <li>a search whose answer carries no count of the pages it matches - one asked with an
 *     {@code srinfo} or {@code gsrinfo} that leaves {@code totalhits} out;</li>
 *     <li>anything past the refusal in a parse naming no {@code formatversion=2}, which the wiki
 *     answers in format version 1: its text is an object and a template names its title under
 *     {@code *}, neither of which {@link WikiParse} binds;</li>
 *     <li>an {@code api.php} answer in any format but {@code format=json}, including the HTML page
 *     the API answers when no format is named;</li>
 *     <li>a rendered page, {@code /w/<title>}, which follows a redirect itself and answers a missing
 *     page with a {@code 404} the fetcher sees;</li>
 *     <li>any URL on a host other than {@link WikiRequest#HOST}, which passes untouched.</li>
 * </ul>
 */
@UtilityClass
public final class WikiResponseGuard {

    private static final @NotNull Gson GSON = SkyBlockWiki.settings().create();

    /**
     * Checks one answer with the checks its request's shape carries evidence for.
     *
     * @param uri the URL the answer was fetched from
     * @param body the answer's body
     * @throws WikiErrorException if an {@code api.php} answer is a refusal
     * @throws WikiMissingPageException if a parse of wikitext reached a document the wiki does not
     *     hold
     * @throws WikiIncludeSizeException if the wiki left part of a parse of wikitext out
     * @throws WikiMissingFooterException if a {@link DplQuery} result in a parse of wikitext wrote
     *     no footer, or one without both counts
     * @throws WikiLimitReachedException if a {@link DplQuery} result in a parse of wikitext stops
     *     short of the pages its query matches, a Bucket answer holds as many rows as its limit, or
     *     a search matches more pages than {@link WikiRequest#MAX_SEARCH_HITS}
     * @throws WikiRedirectException if a page source is a redirect's stub
     * @throws JsonParseException if an {@code api.php} answer asked for in JSON is not JSON
     */
    public static void check(@NotNull URI uri, @NotNull String body) throws WikiException {
        if (!WikiRequest.HOST.equalsIgnoreCase(uri.getHost()))
            return;

        Map<String, String> parameters = parametersOf(uri);
        String path = uri.getPath() == null ? "" : uri.getPath();

        if ("raw".equals(parameters.get("action"))) {
            if (path.startsWith(WikiRequest.PAGE_PATH))
                WikiText.requireSource(path.substring(WikiRequest.PAGE_PATH.length()), body);
            else if (path.equals("/index.php") && parameters.containsKey("title"))
                WikiText.requireSource(parameters.get("title"), body);

            return;
        }

        if (path.equals(WikiRequest.API_PATH) && "json".equals(parameters.get("format")))
            checkApi(parameters, body);
    }

    /**
     * Checks an {@code api.php} answer in JSON by the module its request names.
     *
     * @param parameters the request's parameters, decoded
     * @param body the answer's body
     */
    private static void checkApi(@NotNull Map<String, String> parameters, @NotNull String body) throws WikiException {
        switch (parameters.getOrDefault("action", "")) {
            case "parse" -> {
                if (!isFormatVersionTwo(parameters)) {
                    bind(body, WikiErrorResponse.class).requireSuccess();
                    return;
                }

                WikiParse parse = bind(body, WikiParse.class);
                parse.requireSuccess();

                if (!isWikitextParse(parameters))
                    return;

                parse.requireComplete();

                if (answersText(parameters))
                    parse.requireTableRows(DplQuery.countIn(parameters.get("text")));
            }
            case "query" -> {
                if (!isSearch(parameters)) {
                    bind(body, WikiErrorResponse.class).requireSuccess();
                    return;
                }

                WikiSearchResult search = bind(body, WikiSearchResult.class);
                search.requireSuccess();
                search.requireReachable();
            }
            case "bucket" -> {
                WikiBucket bucket = bind(body, WikiBucket.class);
                bucket.requireSuccess();
                bucket.requireUnderLimit(BucketQuery.limitOf(parameters.getOrDefault("query", "")));
            }
            default -> bind(body, WikiErrorResponse.class).requireSuccess();
        }
    }

    /**
     * Reads a JSON answer as the type its request's shape answers.
     *
     * @param body the answer's body
     * @param type the answer type
     * @param <T> the answer type
     * @return the answer
     * @throws JsonParseException if the body is not a JSON object
     */
    private static <T extends WikiResponse> @NotNull T bind(@NotNull String body, @NotNull Class<T> type) throws JsonParseException {
        T response = GSON.fromJson(body, type);

        if (response == null)
            throw new JsonParseException(String.format("The answer holds no JSON object for '%s'", type.getSimpleName()));

        return response;
    }

    /**
     * Asks whether an answer comes in the format version {@link WikiParse} binds, the one every
     * {@link WikiRequest} parse names.
     *
     * @param parameters the request's parameters, decoded
     * @return {@code true} when the request names {@code formatversion=2}, or {@code latest}, which
     *     the wiki answers as version 2
     */
    private static boolean isFormatVersionTwo(@NotNull Map<String, String> parameters) {
        String version = parameters.getOrDefault("formatversion", "");
        return version.equals("2") || version.equals("latest");
    }

    /**
     * Asks whether a parse renders wikitext rather than a page the wiki holds.
     *
     * @param parameters the parse's parameters, decoded
     * @return {@code true} when the parse carries {@code text} and names no page, revision or page id
     */
    private static boolean isWikitextParse(@NotNull Map<String, String> parameters) {
        return parameters.containsKey("text")
            && !parameters.containsKey("page")
            && !parameters.containsKey("pageid")
            && !parameters.containsKey("oldid");
    }

    /**
     * Asks whether a parse answers its rendered text.
     *
     * @param parameters the parse's parameters, decoded
     * @return {@code true} when the parse names {@code text} among its props, or names no props, which
     *     the wiki answers with its default set, {@code text} among it
     */
    private static boolean answersText(@NotNull Map<String, String> parameters) {
        return !parameters.containsKey("prop") || valuesOf(parameters, "prop").contains("text");
    }

    /**
     * Asks whether a query runs a search, as a list or as the generator of the pages it reads.
     *
     * @param parameters the query's parameters, decoded
     * @return {@code true} when the query names {@code search} among its lists or as its generator
     */
    private static boolean isSearch(@NotNull Map<String, String> parameters) {
        return "search".equals(parameters.get("generator")) || valuesOf(parameters, "list").contains("search");
    }

    /**
     * Reads a multi-valued parameter as its values.
     *
     * @param parameters the request's parameters, decoded
     * @param name the parameter name
     * @return the values the parameter joins with {@code |}, empty when the request does not carry it
     */
    private static @NotNull List<String> valuesOf(@NotNull Map<String, String> parameters, @NotNull String name) {
        return Arrays.asList(parameters.getOrDefault(name, "").split("\\|"));
    }

    /**
     * Reads a URL's query as the wiki reads it: each parameter decoded, a bare name as the empty
     * string, and a repeated name as its last value.
     *
     * @param uri the URL
     * @return the parameters in the order the URL names them
     */
    private static @NotNull Map<String, String> parametersOf(@NotNull URI uri) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        String query = uri.getRawQuery();

        if (query == null || query.isEmpty())
            return parameters;

        for (String pair : query.split("&")) {
            if (pair.isEmpty())
                continue;

            int equals = pair.indexOf('=');
            String name = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            parameters.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }

        return parameters;
    }

}
