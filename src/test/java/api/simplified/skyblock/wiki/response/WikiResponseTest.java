package api.simplified.skyblock.wiki.response;

import api.simplified.skyblock.wiki.exception.WikiApiException;
import api.simplified.skyblock.wiki.exception.WikiErrorException;
import api.simplified.skyblock.wiki.exception.WikiIncludeSizeException;
import api.simplified.skyblock.wiki.exception.WikiLimitReachedException;
import api.simplified.skyblock.wiki.exception.WikiMissingFooterException;
import api.simplified.skyblock.wiki.exception.WikiMissingPageException;
import api.simplified.skyblock.wiki.exception.WikiRedirectException;
import api.simplified.skyblock.wiki.request.BucketQuery;
import api.simplified.skyblock.wiki.request.WikiRequest;
import api.simplified.skyblock.wiki.request.WikiText;
import com.google.gson.JsonObject;
import dev.simplified.client.exception.ErrorContext;
import dev.simplified.client.request.HttpMethod;
import dev.simplified.client.response.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static api.simplified.skyblock.wiki.WikiFixtures.GSON;
import static api.simplified.skyblock.wiki.WikiFixtures.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers how the wiki's answers bind, over answers the wiki gave cut down to a few entries: each
 * response type's fields, the refusal either module writes, and the checks a parse and a refusal
 * raise through.
 */
class WikiResponseTest {

    static <T> @NotNull T bind(@NotNull String name, @NotNull Class<T> type) {
        return GSON.fromJson(fixture(name), type);
    }

    @Test
    @DisplayName("a parse binds the title and page it was parsed as and its rendered text")
    void parseText() {
        WikiParse parse = bind("parse-transclusion.json", WikiParse.class);

        assertThat(parse.getTitle(), equalTo("API"));
        assertThat(parse.getPageId(), equalTo(89143L));
        assertThat(parse.getText(), startsWith("<div class=\"mw-content-ltr mw-parser-output\""));
        assertThat(parse.getTemplates(), empty());
        assertThat(parse.getError().isPresent(), equalTo(false));
    }

    @Test
    @DisplayName("a parse binds every document it transcluded, and one holding all of them is complete")
    void parseTemplates() {
        WikiParse parse = bind("parse-templates.json", WikiParse.class);

        assertThat(parse.getTemplates().stream().map(WikiParse.Template::getTitle).toList(), contains("Harp", "Personal Harp", "Template:For"));
        assertThat(parse.getTemplates().getLast().getNamespace(), equalTo(10));
        assertThat(parse.getTemplates().getFirst().isExisting(), equalTo(true));
        assertThat(parse.getText(), equalTo(""));
        assertThat(parse.requireComplete(), equalTo(parse));
    }

    @Test
    @DisplayName("a parse reaching a missing page or module fails the check, naming each")
    void redLinks() {
        WikiParse parse = bind("parse-red-link.json", WikiParse.class);

        assertThat(parse.getMissingTemplates(), hasSize(3));
        assertThat(parse.getText(), containsString("redlink=1"));

        WikiMissingPageException thrown = assertThrows(WikiMissingPageException.class, parse::requireComplete);
        assertThat(thrown.getMessage(), equalTo(
            "The parse reaches 'No Such Page Zzq', 'No Such Page Zzq2', 'Module:No Such Module Zzq', which the wiki does not hold"
        ));
    }

    @Test
    @DisplayName("a parse the wiki cut short at the post-expand include size fails the check")
    void includeSize() {
        WikiParse parse = bind("parse-include-size.json", WikiParse.class);

        assertThat(parse.isOverIncludeSize(), equalTo(true));
        assertThrows(WikiIncludeSizeException.class, parse::requireComplete);
    }

    @Test
    @DisplayName("a parse rendering a source that quotes the warning is not cut short")
    void quotedIncludeSize() {
        WikiParse parse = GSON.fromJson(
            "{\"parse\":{\"title\":\"API\",\"text\":\"<pre>&lt;!-- WARNING: template omitted, post-expand include size too large --&gt;</pre>"
                + "<p>A page stops at the post-expand include size too large for it.</p>\"}}",
            WikiParse.class
        );

        assertThat(parse.isOverIncludeSize(), equalTo(false));
        assertThat(parse.requireComplete(), equalTo(parse));
    }

    @Test
    @DisplayName("a parse binds its sections")
    void sections() {
        WikiParse parse = bind("parse-sections.json", WikiParse.class);

        assertThat(parse.getTitle(), equalTo("Harp"));
        assertThat(parse.getSections(), hasSize(4));
        assertThat(parse.getSections().getFirst().getLevel(), equalTo(2));
        assertThat(parse.getSections().getFirst().getLine(), equalTo("Songs"));
        assertThat(parse.getSections().get(1).getAnchor(), equalTo("Game_Message"));
        assertThat(parse.getSections().getLast().getFromTitle(), equalTo("Harp"));
        assertThat(parse.getSections().getLast().getIndex(), equalTo("4"));
    }

    @Test
    @DisplayName("MediaWiki's refusal binds as the error, leaves every field at its default, and raises on the check")
    void mediaWikiRefusal() {
        WikiParse parse = bind("parse-missing-title.json", WikiParse.class);

        assertThat(parse.getError().map(WikiError::getCode).orElseThrow(), equalTo("missingtitle"));
        assertThat(parse.getError().map(WikiError::getInfo).orElseThrow(), equalTo("The page you specified doesn't exist."));
        assertThat(parse.getTitle(), equalTo(""));
        assertThat(parse.getText(), equalTo(""));

        WikiErrorException thrown = assertThrows(WikiErrorException.class, parse::requireSuccess);
        assertThat(thrown.getMessage(), equalTo("The wiki refused the request with 'missingtitle' - 'The page you specified doesn't exist.'"));
    }

    @Test
    @DisplayName("a prefix read binds each page with the source of its latest revision")
    void prefixRead() {
        WikiQueryResult result = bind("query-prefix.json", WikiQueryResult.class);

        assertThat(result.getPages().stream().map(WikiQueryResult.Page::getTitle).toList(), contains("Fairy Souls/List/Hub", "Fairy Souls/List/The Farming Islands"));
        assertThat(result.getPages().getFirst().getPageId(), equalTo(116477L));
        assertThat(result.getPages().getFirst().getContent().orElseThrow(), startsWith("{| class=\"wikitable lighttable sortable\""));
        assertThat(result.getPages().getFirst().getRevisions().getFirst().getSlots().get("main").getContentModel(), equalTo("wikitext"));
        assertThat(result.getPages().getFirst().isMissing(), equalTo(false));
        assertThat(result.getContinuation().isEmpty(), equalTo(true));
    }

    @Test
    @DisplayName("a titles read binds each page under its normalized title")
    void titlesRead() {
        WikiQueryResult result = bind("query-titles.json", WikiQueryResult.class);

        assertThat(result.getPages().stream().map(WikiQueryResult.Page::getTitle).toList(), contains("Dungeon Potion", "God Potion"));
        assertThat(result.getPages().getFirst().getContent().orElseThrow(), startsWith("{{Infobox/Item\n"));
    }

    @Test
    @DisplayName("a missing page binds as missing, with no source, and a continuation binds its parameters")
    void missingPageAndContinuation() {
        WikiQueryResult result = GSON.fromJson(
            "{\"continue\":{\"gapcontinue\":\"Fairy_Souls/List/Z\",\"continue\":\"gapcontinue||\",\"sroffset\":50},"
                + "\"query\":{\"pages\":[{\"ns\":0,\"title\":\"No Such Page\",\"missing\":true}]}}",
            WikiQueryResult.class
        );

        assertThat(result.getPages().getFirst().isMissing(), equalTo(true));
        assertThat(result.getPages().getFirst().isInvalid(), equalTo(false));
        assertThat(result.getPages().getFirst().getContent().isPresent(), equalTo(false));
        assertThat(Map.copyOf(result.getContinuation()), equalTo(Map.of("gapcontinue", "Fairy_Souls/List/Z", "continue", "gapcontinue||", "sroffset", "50")));
    }

    @Test
    @DisplayName("an invalid title binds as invalid, neither missing nor holding a source")
    void invalidTitle() {
        WikiQueryResult result = GSON.fromJson(
            "{\"query\":{\"pages\":[{\"title\":\"A[B]\",\"invalidreason\":\"The requested page title contains invalid characters.\",\"invalid\":true}]}}",
            WikiQueryResult.class
        );
        WikiQueryResult.Page page = result.getPages().getFirst();

        assertThat(page.isInvalid(), equalTo(true));
        assertThat(page.isMissing(), equalTo(false));
        assertThat(page.getPageId(), equalTo(0L));
        assertThat(page.getContent().isPresent(), equalTo(false));
    }

    @Test
    @DisplayName("a search binds each hit, in the default format version")
    void search() {
        WikiSearchResult result = bind("search.json", WikiSearchResult.class);

        assertThat(result.getHits().stream().map(WikiSearchResult.Hit::getTitle).toList(), contains("Dungeon Potion", "Haste Potion", "Spelunker Potion"));
        assertThat(result.getHits().getFirst().getPageId(), equalTo(61550L));
        assertThat(result.getHits().getFirst().getSnippet(), containsString("<span class=\"searchmatch\">BUFF}}</span>"));
        assertThat(result.getContinuation().isEmpty(), equalTo(true));
        assertThat(result.getTotalHits(), equalTo(Optional.of(42L)));
        assertThat(result.requireReachable(), equalTo(result));
    }

    /**
     * The wiki serves no hit at an offset of 10,000 or more: a search counting 40,812 pages, asked for
     * five hits at offset 9,998, answered two and no continuation.
     */
    @Test
    @DisplayName("a search counting more pages than the wiki serves hits for fails the check, and one counting exactly that many or none passes")
    void searchReach() {
        WikiSearchResult over = GSON.fromJson("{\"batchcomplete\":\"\",\"query\":{\"searchinfo\":{\"totalhits\":40812},\"search\":[]}}", WikiSearchResult.class);
        WikiSearchResult most = GSON.fromJson("{\"query\":{\"searchinfo\":{\"totalhits\":10000},\"search\":[]}}", WikiSearchResult.class);
        WikiSearchResult uncounted = GSON.fromJson("{\"query\":{\"search\":[]}}", WikiSearchResult.class);

        WikiLimitReachedException thrown = assertThrows(WikiLimitReachedException.class, over::requireReachable);
        assertThat(thrown.getMessage(), equalTo("The search matches '40812' pages, more than the '10000' hits the wiki serves for one search - narrow the query"));
        assertThat(most.requireReachable(), equalTo(most));
        assertThat(uncounted.getTotalHits().isPresent(), equalTo(false));
        assertThat(uncounted.requireReachable(), equalTo(uncounted));
    }

    @Test
    @DisplayName("a read of the pages a search generates binds the count beside the pages, and a read no search generates carries none")
    void generatorSearch() {
        WikiQueryResult result = bind("query-search.json", WikiQueryResult.class);
        WikiQueryResult prefix = bind("query-prefix.json", WikiQueryResult.class);
        WikiQueryResult over = GSON.fromJson("{\"query\":{\"searchinfo\":{\"totalhits\":10001},\"pages\":[]}}", WikiQueryResult.class);

        assertThat(result.getTotalHits(), equalTo(Optional.of(12L)));
        assertThat(result.getPages().stream().map(WikiQueryResult.Page::getTitle).toList(), contains("Cheap Coffee", "Tepid Green Tea"));
        assertThat(result.requireReachable(), equalTo(result));
        assertThat(prefix.getTotalHits().isPresent(), equalTo(false));
        assertThat(prefix.requireReachable(), equalTo(prefix));
        assertThrows(WikiLimitReachedException.class, over::requireReachable);
    }

    @Test
    @DisplayName("a joined Bucket query binds each row as an object keyed by qualified column")
    void bucket() {
        WikiBucket bucket = bind("bucket-join.json", WikiBucket.class);
        JsonObject first = bucket.getRows().getFirst();

        assertThat(bucket.getBucketQuery(), startsWith("bucket('mob_bestiary').join('mob_stats',"));
        assertThat(bucket.getRows(), hasSize(2));
        assertThat(first.get("mob_bestiary.name").getAsString(), equalTo("Magma Cube"));
        assertThat(first.getAsJsonArray("mob_stats.mob_types").get(1).getAsString(), equalTo("Cubic"));
    }

    @Test
    @DisplayName("Bucket's refusal, a bare string, binds as the error's info and raises on the check")
    void bucketRefusal() {
        WikiBucket bucket = bind("bucket-error.json", WikiBucket.class);

        assertThat(bucket.getError().map(WikiError::getCode).orElseThrow(), equalTo(""));
        assertThat(bucket.getError().map(WikiError::getInfo).orElseThrow(), equalTo("Bucket no_such_bucket_zzq does not exist."));
        assertThat(bucket.getRows(), empty());
        assertThrows(WikiErrorException.class, bucket::requireSuccess);
    }

    @Test
    @DisplayName("an empty string binds as empty rather than absent")
    void emptyString() {
        WikiSearchResult result = GSON.fromJson("{\"query\":{\"search\":[{\"ns\":0,\"title\":\"X\",\"pageid\":1,\"snippet\":\"\"}]}}", WikiSearchResult.class);

        assertThat(result.getHits().getFirst().getSnippet(), equalTo(""));
    }

    @Test
    @DisplayName("a Bucket answer under its limit passes, and one holding as many rows as its limit fails the check")
    void bucketLimit() {
        WikiBucket bucket = bind("bucket-join.json", WikiBucket.class);

        assertThat(bucket.requireUnderLimit(3), equalTo(bucket));

        WikiLimitReachedException raise = assertThrows(WikiLimitReachedException.class, () -> bucket.requireUnderLimit(2));
        assertThat(raise.getMessage(), equalTo("The Bucket query answered '2' rows, as many as its limit, and may match more - raise the limit, up to '5000'"));
        assertThrows(WikiLimitReachedException.class, () -> bucket.requireUnderLimit(1));
    }

    /**
     * Bucket lowers a limit over {@link BucketQuery#MAX_LIMIT} to it and runs a limit that is not
     * positive at {@link BucketQuery#DEFAULT_LIMIT} - {@code .limit(0)} over {@code mob_bestiary}
     * answered all 392 of its rows - so the check reads a limit the same way.
     */
    @Test
    @DisplayName("a Bucket answer is checked against the limit as Bucket runs it, not as a caller names it")
    void bucketLimitAsRun() {
        WikiBucket bucket = bind("bucket-join.json", WikiBucket.class);
        WikiBucket capped = GSON.fromJson(
            "{\"bucketQuery\":\"x\",\"bucket\":[" + "{\"a\":\"1\"},".repeat(BucketQuery.MAX_LIMIT - 1) + "{\"a\":\"1\"}]}",
            WikiBucket.class
        );

        WikiLimitReachedException thrown = assertThrows(WikiLimitReachedException.class, () -> capped.requireUnderLimit(9000));
        assertThat(thrown.getMessage(), equalTo("The Bucket query answered '5000' rows, as many as its limit, and may match more - narrow the query, as '5000' is the most one query answers"));
        assertThat(bucket.requireUnderLimit(0), equalTo(bucket));
        assertThat(bucket.requireUnderLimit(-1), equalTo(bucket));
    }

    @Test
    @DisplayName("a parse binds the footer of every DynamicPageList result, and one short of its matches fails the check")
    void dplTables() {
        WikiParse parse = bind("parse-dpl-tables.json", WikiParse.class);

        assertThat(parse.getTableRows(), contains(
            new WikiParse.TableRows(3, 22),
            new WikiParse.TableRows(22, 22),
            new WikiParse.TableRows(2, 28)
        ));
        assertThat(parse.getTableRows().getFirst().isCut(), equalTo(true));
        assertThat(parse.getTableRows().get(1).isCut(), equalTo(false));
        assertThat(parse.getMissingTemplates(), empty());
        assertThat(parse.isOverIncludeSize(), equalTo(false));

        WikiLimitReachedException thrown = assertThrows(WikiLimitReachedException.class, parse::requireComplete);
        assertThat(thrown.getMessage(), equalTo(
            "A DynamicPageList result holds '3' of the '22' pages its query matches - raise its count, up to '500', or narrow the query"
        ));
    }

    @Test
    @DisplayName("a parse holding only whole DynamicPageList results, or a quoted footer, is complete")
    void wholeDplTables() {
        WikiParse whole = GSON.fromJson(
            "{\"parse\":{\"title\":\"API\",\"text\":\"<table></table><p><span class=\\\"dplquery-rows\\\" data-rows=\\\"22\\\" data-total=\\\"22\\\"></span>\\n</p>\"}}",
            WikiParse.class
        );
        WikiParse quoted = GSON.fromJson(
            "{\"parse\":{\"title\":\"API\",\"text\":\"<pre>&lt;span class=\\\"dplquery-rows\\\" data-rows=\\\"3\\\" data-total=\\\"22\\\"&gt;&lt;/span&gt;</pre>\"}}",
            WikiParse.class
        );

        assertThat(whole.requireComplete(), equalTo(whole));
        assertThat(quoted.getTableRows(), empty());
        assertThat(quoted.requireComplete(), equalTo(quoted));
    }

    /**
     * {@code parse-dpl-footers.json} answers three queries: one of count one, one over
     * {@code Template:Admins}, which the wiki holds and no page uses, whose {@code noresultsfooter}
     * wrote {@code 0} of {@code 0}, and one of count three.
     */
    @Test
    @DisplayName("a parse binds a footer for every DynamicPageList query, one matching nothing included, and asks for one per query")
    void dplFooterPerQuery() {
        WikiParse parse = bind("parse-dpl-footers.json", WikiParse.class);

        assertThat(parse.getTableRows(), contains(
            new WikiParse.TableRows(1, 22),
            new WikiParse.TableRows(0, 0),
            new WikiParse.TableRows(3, 22)
        ));
        assertThat(parse.getTableRows().get(1).isCut(), equalTo(false));
        assertThat(parse.getMissingTemplates(), empty());
        assertThat(parse.requireTableRows(3), equalTo(parse));

        WikiMissingFooterException missing = assertThrows(WikiMissingFooterException.class, () -> parse.requireTableRows(4));
        assertThat(missing.getMessage(), equalTo(
            "The parse renders '4' DynamicPageList queries and its text holds '3' filled footers, so whether each result stops short of the pages its query matches cannot be read"
        ));
        assertThrows(WikiLimitReachedException.class, parse::requireComplete);
    }

    @Test
    @DisplayName("a parse holding no footer passes for no query and fails for one, and a footer the wiki left unfilled fails the check")
    void dplFooterMissingOrUnfilled() {
        WikiParse none = bind("parse-transclusion.json", WikiParse.class);
        WikiParse unfilled = GSON.fromJson(
            "{\"parse\":{\"title\":\"API\",\"text\":\"<table></table><p><span class=\\\"dplquery-rows\\\" data-rows=\\\"3\\\" data-total=\\\"%TOTALPAGES%\\\"></span>\\n</p>\"}}",
            WikiParse.class
        );

        assertThat(none.requireTableRows(0), equalTo(none));
        assertThrows(WikiMissingFooterException.class, () -> none.requireTableRows(1));
        assertThat(unfilled.getTableRows(), empty());

        WikiMissingFooterException thrown = assertThrows(WikiMissingFooterException.class, unfilled::requireComplete);
        assertThat(thrown.getMessage(), equalTo(
            "A DynamicPageList result wrote its footer without both counts, '<span class=\"dplquery-rows\" data-rows=\"3\" data-total=\"%TOTALPAGES%\">', so whether it stops short of the pages its query matches cannot be read"
        ));
        assertThrows(WikiMissingFooterException.class, () -> unfilled.requireTableRows(1));
    }

    @Test
    @DisplayName("a parse of a page that names a redirect binds the redirect and the target it parsed")
    void parseRedirect() {
        WikiParse parse = bind("parse-redirect.json", WikiParse.class);

        assertThat(parse.getTitle(), equalTo("Chocolate Factory"));
        assertThat(parse.getRedirects(), hasSize(1));
        assertThat(parse.getRedirects().getFirst().getFrom(), equalTo("Hoppity's Collection"));
        assertThat(parse.getRedirects().getFirst().getTo(), equalTo("Chocolate Factory"));
        assertThat(parse.getRedirects().getFirst().getFragment(), equalTo("Hoppity's Collection"));
    }

    @Test
    @DisplayName("a read of titles following redirects binds each redirect and answers its target in its place")
    void queryRedirect() {
        WikiQueryResult result = bind("query-redirect.json", WikiQueryResult.class);

        assertThat(result.getPages().stream().map(WikiQueryResult.Page::getTitle).toList(), contains("No Such Page Zzq", "Harp", "Chocolate Factory"));
        assertThat(result.getRedirects().stream().map(WikiRedirect::getFrom).toList(), contains("Hoppity's Collection"));
        assertThat(result.getRedirects().getFirst().getTo(), equalTo("Chocolate Factory"));
        assertThat(result.getPages().getLast().getContent().orElseThrow(), equalTo("{{Infobox/Generic"));
    }

    @Test
    @DisplayName("a redirect's stub names its target, in any case and spacing MediaWiki reads, and nothing else is one")
    void redirectTarget() {
        assertThat(WikiText.redirectTarget(fixture("raw-redirect.txt")).orElseThrow(), equalTo("Chocolate Factory#Hoppity's Collection"));
        assertThat(WikiText.redirectTarget("#redirect[[Harp]]").orElseThrow(), equalTo("Harp"));
        assertThat(WikiText.redirectTarget("\n  #ReDiReCt : [[ Personal Harp | the harp ]]\n[[Category:Redirects]]").orElseThrow(), equalTo("Personal Harp"));
        assertThat(WikiText.redirectTarget("{{Infobox/Item}}\n#REDIRECT [[Harp]]").isPresent(), equalTo(false));
        assertThat(WikiText.redirectTarget("#REDIRECTS are pages that lead elsewhere").isPresent(), equalTo(false));
        assertThat(WikiText.redirectTarget("#REDIRECT [[]]").isPresent(), equalTo(false));

        WikiRedirectException thrown = assertThrows(WikiRedirectException.class, () -> WikiText.requireSource("Hoppity's Collection", fixture("raw-redirect.txt")));
        assertThat(thrown.getMessage(), equalTo("The page 'Hoppity's_Collection' is a redirect to 'Chocolate Factory#Hoppity's Collection'"));
        assertThat(WikiText.requireSource("Harp", "{{For|x}}"), equalTo("{{For|x}}"));
    }

    /**
     * Scribunto stores a module's redirect as one Lua statement rather than a {@code #REDIRECT};
     * {@code Module:Statname/Data} answers {@code return require [[Module:Stat/Data]]} read raw,
     * with a status of 200.
     */
    @Test
    @DisplayName("a module's redirect stored as Lua names its target, and a module that only reads another is not one")
    void moduleRedirectTarget() {
        assertThat(WikiText.redirectTarget(fixture("raw-module-redirect.txt")).orElseThrow(), equalTo("Module:Stat/Data"));
        assertThat(WikiText.redirectTarget("return require [[Module:Stat/Data]]\n").orElseThrow(), equalTo("Module:Stat/Data"));
        assertThat(WikiText.redirectTarget("return require('Module:Stat/Data')").isPresent(), equalTo(false));
        assertThat(WikiText.redirectTarget("local data = require [[Module:Stat/Data]]\nreturn data").isPresent(), equalTo(false));
        assertThat(WikiText.redirectTarget("return require [[Module:Stat/Data]].stats").isPresent(), equalTo(false));
        assertThat(WikiText.redirectTarget("return require [[Stat/Data]]").isPresent(), equalTo(false));

        WikiRedirectException thrown = assertThrows(WikiRedirectException.class, () -> WikiText.requireSource("Module:Statname/Data", fixture("raw-module-redirect.txt")));
        assertThat(thrown.getMessage(), equalTo("The page 'Module:Statname/Data' is a redirect to 'Module:Stat/Data'"));
    }

    @Test
    @DisplayName("an error status reads a MediaWiki error envelope, and falls back on any other body")
    void errorStatus() {
        WikiApiException envelope = failure(503, "{\"error\":{\"code\":\"maxlag\",\"info\":\"Waiting for a database server\"}}");
        WikiApiException page = failure(414, "<html><head><title>414 Request-URI Too Large</title></head></html>");

        assertThat(envelope.getResponse().getReason(), equalTo("Waiting for a database server"));
        assertThat(envelope.getResponse().getError().map(WikiError::getCode).orElseThrow(), equalTo("maxlag"));
        assertThat(page.getResponse().getReason(), equalTo("Unknown (body missing or not a MediaWiki error)"));
        assertThat(page.getStatus().getCode(), equalTo(414));
    }

    private static @NotNull WikiApiException failure(int status, @NotNull String body) {
        return new WikiApiException(GSON, new ErrorContext(
            HttpStatus.of(status),
            HttpMethod.GET,
            WikiRequest.getPage("Harp").getUrl(),
            Map.of(),
            Map.of(),
            body.getBytes(StandardCharsets.UTF_8)
        ));
    }

}
