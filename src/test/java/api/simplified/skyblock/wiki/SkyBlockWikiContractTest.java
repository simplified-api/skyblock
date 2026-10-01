package api.simplified.skyblock.wiki;

import api.simplified.skyblock.wiki.client.SkyBlockWiki;
import api.simplified.skyblock.wiki.client.WikiPacing;
import api.simplified.skyblock.wiki.exception.WikiApiException;
import api.simplified.skyblock.wiki.exception.WikiErrorException;
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
import api.simplified.skyblock.wiki.response.WikiRedirect;
import api.simplified.skyblock.wiki.response.WikiSearchResult;
import dev.simplified.client.ClientConfig;
import dev.simplified.client.cache.ResponseCache;
import dev.simplified.client.decoder.InternalErrorDecoder;
import dev.simplified.client.decoder.InternalResponseDecoder;
import dev.simplified.client.exception.ErrorContext;
import dev.simplified.client.exception.RateLimitException;
import dev.simplified.client.interceptor.InternalRequestInterceptor;
import dev.simplified.client.ratelimit.RateLimit;
import dev.simplified.client.ratelimit.RateLimitManager;
import dev.simplified.client.ratelimit.RateLimitingFeignClient;
import dev.simplified.client.request.HttpMethod;
import dev.simplified.client.response.HttpStatus;
import dev.simplified.client.route.RouteDiscovery;
import feign.Feign;
import feign.Request;
import feign.Response;
import feign.Target;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static api.simplified.skyblock.wiki.WikiFixtures.GSON;
import static api.simplified.skyblock.wiki.WikiFixtures.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers what the contract's typed methods send and what they raise: the checks each runs on an
 * answer, the walk through every part of a paged answer, and the request Feign builds from a
 * {@link WikiRequest} - one the wiki decodes to the same parameters as the request's URL.
 *
 * <p>
 * The wiki answers from memory, through an implementation of the contract's four {@code api.php}
 * methods and two page methods, or through a Feign transport that records each request beneath the
 * client's own configuration, so no request leaves the machine.
 */
class SkyBlockWikiContractTest {

    private FakeWiki wiki;

    @BeforeEach
    void setUp() {
        this.wiki = new FakeWiki();
    }

    @Nested
    @DisplayName("checks a composite")
    class Composites {

        @Test
        @DisplayName("sending the composite's request and answering one that holds everything it names")
        void complete() {
            WikiParse parse = SkyBlockWikiContractTest.this.wiki.answering(fixture("parse-templates.json")).transclude("Harp", "Personal_Harp");

            assertThat(parse.getTemplates(), hasSize(3));
            assertThat(SkyBlockWikiContractTest.this.wiki.sent, contains(parameters(WikiRequest.transclude("Harp", "Personal_Harp"))));
        }

        @Test
        @DisplayName("raising a red link from a transclusion and from a table")
        void redLink() {
            assertThrows(WikiMissingPageException.class, () -> SkyBlockWikiContractTest.this.wiki.answering(fixture("parse-red-link.json")).transclude("No Such Page Zzq"));
            assertThrows(WikiMissingPageException.class, () -> SkyBlockWikiContractTest.this.wiki.answering(fixture("parse-red-link.json")).templateTable(DplQuery.uses("X")));
        }

        @Test
        @DisplayName("raising a composite the wiki cut short at the post-expand include size")
        void includeSize() {
            assertThrows(WikiIncludeSizeException.class, () -> SkyBlockWikiContractTest.this.wiki.answering(fixture("parse-include-size.json")).moduleSources("Reforge/Data"));
        }

        @Test
        @DisplayName("raising from a composite request sent through parse, and not from loose wikitext sent the same way")
        void throughParse() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;

            assertThrows(WikiMissingPageException.class, () -> wiki.answering(fixture("parse-red-link.json")).parse(WikiRequest.transclude("No Such Page Zzq")));
            assertThrows(WikiIncludeSizeException.class, () -> wiki.answering(fixture("parse-include-size.json")).parse(WikiRequest.templateTable(DplQuery.uses("X"))));
            assertThat(
                wiki.answering(fixture("parse-red-link.json")).parse(WikiRequest.parseWikitext(WikiText.transclusion("No Such Page Zzq"))).getMissingTemplates(),
                hasSize(3)
            );
        }

        @Test
        @DisplayName("but leaving loose wikitext to its caller")
        void looseWikitext() {
            WikiParse parse = SkyBlockWikiContractTest.this.wiki.answering(fixture("parse-red-link.json")).parseWikitext("{{:No Such Page Zzq}}");

            assertThat(parse.getMissingTemplates(), hasSize(3));
        }

    }

    @Nested
    @DisplayName("raises a refusal answered as a success")
    class Refusals {

        @Test
        @DisplayName("from MediaWiki, through every method that takes a request, but not through the bound method")
        void mediaWiki() {
            assertThrows(WikiErrorException.class, () -> SkyBlockWikiContractTest.this.wiki.answering(fixture("parse-missing-title.json")).parsePage("No Such Page Zzq"));

            WikiParse unchecked = SkyBlockWikiContractTest.this.wiki.answering(fixture("parse-missing-title.json"))
                .getParse(WikiRequest.parsePage("No Such Page Zzq").getParameters());
            assertThat(unchecked.getError().isPresent(), equalTo(true));
        }

        @Test
        @DisplayName("from Bucket")
        void bucket() {
            assertThrows(WikiErrorException.class, () -> SkyBlockWikiContractTest.this.wiki.answering(fixture("bucket-error.json")).queryBucket(BucketQuery.from("no_such_bucket_zzq").select("a")));
            assertThat(SkyBlockWikiContractTest.this.wiki.answering(fixture("bucket-join.json")).queryBucket(BucketQuery.from("mob_bestiary")).getRows(), hasSize(2));
        }

        @Test
        @DisplayName("from a later part of a paged answer")
        void laterPart() {
            SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[{\"ns\":0,\"title\":\"A\",\"pageid\":1,\"snippet\":\"\"}]}}",
                fixture("parse-missing-title.json")
            );

            assertThrows(WikiErrorException.class, () -> SkyBlockWikiContractTest.this.wiki.search("x"));
        }

    }

    @Nested
    @DisplayName("walks every part of a paged answer")
    class Continuation {

        @Test
        @DisplayName("merging page sources that arrive in a later part, and continuing from the original request each time")
        void query() {
            SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"rvcontinue\":\"2|20\",\"continue\":\"||\"},\"query\":{\"pages\":["
                    + page(1, "A", "a") + "," + page(2, "B", null) + "]}}",
                "{\"continue\":{\"gapcontinue\":\"C\",\"continue\":\"gapcontinue||\"},\"query\":{\"pages\":["
                    + page(1, "A", null) + "," + page(2, "B", "b") + "]}}",
                "{\"batchcomplete\":true,\"query\":{\"pages\":[" + page(3, "C", "c") + "]}}"
            );

            WikiRequest request = WikiRequest.getContentsByPrefix("", 0);
            WikiQueryResult result = SkyBlockWikiContractTest.this.wiki.query(request);

            assertThat(result.getPages().stream().map(WikiQueryResult.Page::getTitle).toList(), contains("A", "B", "C"));
            assertThat(result.getPages().stream().map(page -> page.getContent().orElseThrow()).toList(), contains("a", "b", "c"));
            assertThat(result.getContinuation().isEmpty(), equalTo(true));

            List<Map<String, String>> sent = SkyBlockWikiContractTest.this.wiki.sent;
            assertThat(sent, hasSize(3));
            assertThat(sent.getFirst(), equalTo(parameters(request)));
            assertThat(sent.get(1), equalTo(parameters(request.continuing(Map.of("rvcontinue", "2|20", "continue", "||")))));
            assertThat(sent.get(2).get("gapcontinue"), equalTo("C"));
            assertThat(sent.get(2), not(hasKey("rvcontinue")));
        }

        @Test
        @DisplayName("keeping the redirect each part names, each once")
        void redirects() {
            String redirect = "{\"from\":\"Old\",\"to\":\"A\"}";
            SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"rvcontinue\":\"2|20\",\"continue\":\"||\"},\"query\":{\"redirects\":[" + redirect + "],\"pages\":[" + page(1, "A", "a") + "]}}",
                "{\"query\":{\"redirects\":[" + redirect + ",{\"from\":\"Older\",\"to\":\"B\",\"tofragment\":\"Top\"}],\"pages\":[" + page(2, "B", "b") + "]}}"
            );

            WikiQueryResult result = SkyBlockWikiContractTest.this.wiki.getContents("Old", "Older");

            assertThat(result.getRedirects().stream().map(WikiRedirect::getFrom).toList(), contains("Old", "Older"));
            assertThat(result.getRedirects().getLast().getFragment(), equalTo("Top"));
        }

        @Test
        @DisplayName("appending the hits of every part in order")
        void search() {
            SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":2,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "," + hit("B") + "]}}",
                "{\"query\":{\"search\":[" + hit("C") + "]}}"
            );

            WikiSearchResult result = SkyBlockWikiContractTest.this.wiki.search("insource:/BUFF\\}\\}/");

            assertThat(result.getHits().stream().map(WikiSearchResult.Hit::getTitle).toList(), contains("A", "B", "C"));
            assertThat(SkyBlockWikiContractTest.this.wiki.sent.getLast().get("sroffset"), equalTo("2"));
        }

        @Test
        @DisplayName("keeping each page once when a later part repeats one an earlier part found")
        void searchRepeat() {
            SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":2,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "," + hit("B") + "]}}",
                "{\"query\":{\"search\":[" + hit("B") + "," + hit("C") + "]}}"
            );

            WikiSearchResult result = SkyBlockWikiContractTest.this.wiki.search("x");

            assertThat(result.getHits().stream().map(WikiSearchResult.Hit::getTitle).toList(), contains("A", "B", "C"));
        }

        @Test
        @DisplayName("refusing a part that answers the continuation it was asked with")
        void noProgress() {
            String stuck = "{\"continue\":{\"sroffset\":2,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}";
            SkyBlockWikiContractTest.this.wiki.answering(stuck, stuck);

            assertThrows(IllegalStateException.class, () -> SkyBlockWikiContractTest.this.wiki.search("x"));
            assertThat(SkyBlockWikiContractTest.this.wiki.sent, hasSize(2));
        }

        @Test
        @DisplayName("refusing a part that answers a continuation an earlier part answered, with another between them")
        void cycle() {
            String first = "{\"continue\":{\"rvcontinue\":\"1|10\",\"continue\":\"||\"},\"query\":{\"pages\":[" + page(1, "A", "a") + "]}}";
            String second = "{\"continue\":{\"rvcontinue\":\"2|20\",\"continue\":\"||\"},\"query\":{\"pages\":[" + page(2, "B", "b") + "]}}";
            SkyBlockWikiContractTest.this.wiki.answering(first, second, first, second);

            assertThrows(IllegalStateException.class, () -> SkyBlockWikiContractTest.this.wiki.getContentsByPrefix("", 0));
            assertThat(SkyBlockWikiContractTest.this.wiki.sent, hasSize(3));
        }

        /**
         * A continuation adds parameters to the request it continues, so a request that fits the
         * URL cap can continue past it, which the wiki would refuse; the read fails there rather
         * than sending it.
         */
        @Test
        @DisplayName("raising a continuation that renders the request longer than the wiki accepts, having sent the part before it")
        void continuationOverLength() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;
            WikiRequest request = WikiRequest.search("a".repeat(WikiRequest.MAX_URL_LENGTH - WikiRequest.search("").getUrl().length()));
            wiki.answering("{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}");

            assertThat(request.getUrl().length(), equalTo(WikiRequest.MAX_URL_LENGTH));
            assertThrows(IllegalArgumentException.class, () -> wiki.search(request));
            assertThat(wiki.sent, hasSize(1));
        }

    }

    /**
     * Refuses parts through queued {@link RateLimitException}s and waits on a clock that only the
     * waits move, so nothing sleeps.
     */
    @Nested
    @DisplayName("waits out the rate limit before every part of a paged read")
    class Paging {

        private final @NotNull FakeTime time = new FakeTime();

        @Test
        @DisplayName("sending the refused part again once the wait is over, and answering every part")
        void waitsAndResends() {
            String first = "{\"continue\":{\"gapcontinue\":\"B\",\"continue\":\"gapcontinue||\"},\"query\":{\"pages\":[" + page(1, "A", "a") + "]}}";
            String second = "{\"batchcomplete\":true,\"query\":{\"pages\":[" + page(2, "B", "b") + "]}}";
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(first, refusal(), refusal(), second);
            WikiRequest request = WikiRequest.getContentsByPrefix("", 0);

            WikiQueryResult result = wiki.query(request, this.time.pacing(Duration.ofMinutes(2)));

            assertThat(result.getPages().stream().map(WikiQueryResult.Page::getTitle).toList(), contains("A", "B"));
            assertThat(this.time.sleeps, contains(Duration.ofSeconds(1), Duration.ofSeconds(1)));

            Map<String, String> continued = parameters(request.continuing(Map.of("gapcontinue", "B", "continue", "gapcontinue||")));
            assertThat(wiki.refused, contains(continued, continued));
            assertThat(wiki.sent, contains(parameters(request), continued));
        }

        @Test
        @DisplayName("through a search as through a read of page sources")
        void search() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}",
                refusal(),
                "{\"query\":{\"search\":[" + hit("B") + "]}}"
            );

            WikiSearchResult result = wiki.search(WikiRequest.search("x"), this.time.pacing(Duration.ofMinutes(2)));

            assertThat(result.getHits().stream().map(WikiSearchResult.Hit::getTitle).toList(), contains("A", "B"));
            assertThat(this.time.sleeps, contains(Duration.ofSeconds(1)));
        }

        @Test
        @DisplayName("until the reset instant a refusal's policy names, where it names one")
        void resetInstant() {
            RateLimit advertised = RateLimit.fromHeaders(60, 30, this.time.now.toEpochMilli());
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}",
                new RateLimitException(WikiRequest.HOST, advertised),
                "{\"query\":{\"search\":[" + hit("B") + "]}}"
            );

            wiki.search(WikiRequest.search("x"), this.time.pacing(Duration.ofMinutes(2)));

            assertThat(this.time.sleeps, contains(Duration.ofSeconds(30)));
        }

        @Test
        @DisplayName("waiting out the refusals of a first part as of any other, and sending it again")
        void firstPart() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(
                refusal(),
                refusal(),
                "{\"batchcomplete\":true,\"query\":{\"pages\":[" + page(1, "A", "a") + "]}}"
            );
            WikiRequest request = WikiRequest.getContents("A");

            WikiQueryResult result = wiki.query(request, this.time.pacing(Duration.ofMinutes(2)));

            assertThat(result.getPages().stream().map(WikiQueryResult.Page::getTitle).toList(), contains("A"));
            assertThat(this.time.sleeps, contains(Duration.ofSeconds(1), Duration.ofSeconds(1)));
            assertThat(wiki.refused, contains(parameters(request), parameters(request)));
            assertThat(wiki.sent, contains(parameters(request)));
        }

        @Test
        @DisplayName("and raising a first part still refused once the longest wait has passed, having waited no longer")
        void firstPartBounded() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;
            IntStream.range(0, 10).forEach(i -> wiki.answering(refusal()));

            assertThrows(RateLimitException.class, () -> wiki.search(WikiRequest.search("x"), this.time.pacing(Duration.ofMillis(2500))));
            assertThat(this.time.sleeps, contains(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500)));
            assertThat(wiki.refused, hasSize(4));
            assertThat(wiki.sent, empty());
        }

        @Test
        @DisplayName("while a request that is not part of a paged read raises its refusal at once, sending nothing again")
        void singleRequest() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(refusal(), fixture("parse-templates.json"));

            assertThrows(RateLimitException.class, () -> wiki.transclude("Harp", "Personal_Harp"));
            assertThat(wiki.refused, hasSize(1));
            assertThat(wiki.sent, empty());

            wiki.answers.clear();
            wiki.answering(refusal(), fixture("bucket-join.json"));
            assertThrows(RateLimitException.class, () -> wiki.queryBucket(BucketQuery.from("mob_bestiary")));
            assertThat(wiki.refused, hasSize(2));
            assertThat(wiki.sent, empty());
        }

        @Test
        @DisplayName("and raising a part still refused once the longest wait has passed, having waited no longer")
        void bounded() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering("{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}");
            IntStream.range(0, 10).forEach(i -> wiki.answering(refusal()));

            assertThrows(RateLimitException.class, () -> wiki.search(WikiRequest.search("x"), this.time.pacing(Duration.ofMillis(4500))));
            assertThat(this.time.sleeps, contains(
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500)
            ));
            assertThat(wiki.refused, hasSize(6));
        }

        @Test
        @DisplayName("and raising a later part's first refusal when the longest wait is nothing")
        void noWait() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}",
                refusal(),
                "{\"query\":{\"search\":[" + hit("B") + "]}}"
            );

            assertThrows(RateLimitException.class, () -> wiki.search(WikiRequest.search("x"), this.time.pacing(Duration.ZERO)));
            assertThat(this.time.sleeps, empty());
            assertThat(wiki.refused, hasSize(1));
        }

        @Test
        @DisplayName("and raising a 429 the wiki answered at once, a request having been sent")
        void serverEnforced() {
            RouteDiscovery routes = new RouteDiscovery(SkyBlockWiki.config());
            RateLimitException answered = new RateLimitException(
                new ErrorContext(HttpStatus.of(429), HttpMethod.GET, WikiRequest.search("x").getUrl(), Map.of(), Map.of(), new byte[0]),
                routes.getDefaultRoute()
            );
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}",
                answered
            );

            assertThat(answered.isServerEnforced(), equalTo(true));
            assertThrows(RateLimitException.class, () -> wiki.search(WikiRequest.search("x"), this.time.pacing(Duration.ofMinutes(2))));
            assertThat(this.time.sleeps, empty());
        }

        @Test
        @DisplayName("and raising the refusal when a wait is interrupted, the interrupt kept")
        void interrupted() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(
                "{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}",
                refusal()
            );
            WikiPacing pacing = WikiPacing.of(() -> this.time.now, duration -> { throw new InterruptedException(); }, Duration.ofMinutes(2));

            try {
                RateLimitException thrown = assertThrows(RateLimitException.class, () -> wiki.search(WikiRequest.search("x"), pacing));

                assertThat(thrown.getSuppressed()[0], instanceOf(InterruptedException.class));
                assertThat(Thread.currentThread().isInterrupted(), equalTo(true));
            } finally {
                Thread.interrupted();
            }
        }

        private static @NotNull RateLimitException refusal() {
            return new RateLimitException(WikiRequest.HOST, new RateLimit(60, 60, ChronoUnit.SECONDS));
        }

    }

    @Nested
    @DisplayName("raises a redirect read in place of a page's source")
    class Redirects {

        @Test
        @DisplayName("naming the target, from a page and from a module, while the unchecked read answers the stub")
        void stub() {
            String stub = fixture("raw-redirect.txt");
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.holding(Map.of("Hoppity's_Collection", stub, "Module:Old/Data", "#REDIRECT [[Module:New/Data]]"));

            WikiRedirectException thrown = assertThrows(WikiRedirectException.class, () -> wiki.getWikitext("Hoppity's Collection"));
            assertThat(thrown.getMessage(), containsString("'Chocolate Factory#Hoppity's Collection'"));
            assertThrows(WikiRedirectException.class, () -> wiki.getModuleSource("Old/Data"));
            assertThat(wiki.getSource("Hoppity's Collection"), equalTo(stub));
            assertThat(wiki.getWikitext("Harp"), equalTo("-- Harp"));
        }

        @Test
        @DisplayName("from a module Scribunto stores as a redirect, naming the target")
        void moduleStub() {
            String stub = fixture("raw-module-redirect.txt");
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.holding(Map.of("Module:Statname/Data", stub));

            WikiRedirectException thrown = assertThrows(WikiRedirectException.class, () -> wiki.getModuleSource("Statname/Data"));
            assertThat(thrown.getMessage(), equalTo("The page 'Module:Statname/Data' is a redirect to 'Module:Stat/Data'"));
            assertThat(wiki.getSource("Module:Statname/Data"), equalTo(stub));
            assertThat(wiki.getModuleSource("Stat/Data"), equalTo("-- Module:Stat/Data"));
        }

    }

    @Nested
    @DisplayName("raises an answer stopped at its limit")
    class Limits {

        @Test
        @DisplayName("from a Bucket query answering as many rows as its limit, through the request as through the query")
        void bucket() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;

            assertThrows(WikiLimitReachedException.class, () -> wiki.answering(fixture("bucket-join.json")).queryBucket(BucketQuery.from("mob_bestiary").limit(2)));
            assertThrows(WikiLimitReachedException.class, () -> wiki.answering(fixture("bucket-join.json")).bucket(WikiRequest.queryBucket(BucketQuery.from("mob_bestiary").limit(1))));
            assertThat(wiki.answering(fixture("bucket-join.json")).queryBucket(BucketQuery.from("mob_bestiary").limit(3)).getRows(), hasSize(2));
            assertThat(wiki.sent.getLast().get("query"), containsString("limit%283%29"));
        }

        @Test
        @DisplayName("from a DynamicPageList table short of its matches, and not from loose wikitext holding it")
        void table() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;

            assertThrows(WikiLimitReachedException.class, () -> wiki.answering(fixture("parse-dpl-tables.json")).templateTable(DplQuery.uses("Infobox/Power stone").count(3)));
            assertThat(wiki.answering(fixture("parse-dpl-tables.json")).parseWikitext(DplQuery.uses("Infobox/Power stone").count(3).render()).getTableRows(), hasSize(3));
        }

        /**
         * A search matching more pages than the wiki serves hits for ends its walk at the 10,000th
         * hit with no continuation, so the count on the first part is the one sign of it.
         */
        @Test
        @DisplayName("from a search matching more pages than the wiki serves hits for, on the first part that counts them")
        void searchReach() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;
            wiki.answering(
                "{\"continue\":{\"sroffset\":500,\"continue\":\"-||\"},\"query\":{\"searchinfo\":{\"totalhits\":40812},\"search\":[" + hit("A") + "]}}",
                "{\"query\":{\"searchinfo\":{\"totalhits\":40812},\"search\":[" + hit("B") + "]}}"
            );

            WikiLimitReachedException thrown = assertThrows(WikiLimitReachedException.class, () -> wiki.search("the"));
            assertThat(thrown.getMessage(), containsString("'40812' pages"));
            assertThat(wiki.sent, hasSize(1));
            assertThat(wiki.sent.getFirst(), hasEntry("srinfo", "totalhits"));

            wiki.answers.clear();
            wiki.answering(
                "{\"continue\":{\"gsroffset\":50,\"continue\":\"gsroffset||\"},\"query\":{\"searchinfo\":{\"totalhits\":9990},\"pages\":[" + page(1, "A", "a") + "]}}",
                "{\"query\":{\"searchinfo\":{\"totalhits\":10001},\"pages\":[" + page(2, "B", "b") + "]}}"
            );
            assertThrows(WikiLimitReachedException.class, () -> wiki.searchContents("the"));
            assertThat(wiki.sent, hasSize(3));
            assertThat(wiki.sent.getLast(), hasEntry("gsrinfo", "totalhits"));
        }

        /**
         * The count is read again on every part: an index that grows past the most hits the wiki
         * serves while a search is walked ends the walk as short as one that started past it.
         */
        @Test
        @DisplayName("from a later part of a search, and from the first part of a read a search generates, sending nothing after it")
        void searchReachLater() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;
            wiki.answering(
                "{\"continue\":{\"sroffset\":500,\"continue\":\"-||\"},\"query\":{\"searchinfo\":{\"totalhits\":9990},\"search\":[" + hit("A") + "]}}",
                "{\"continue\":{\"sroffset\":1000,\"continue\":\"-||\"},\"query\":{\"searchinfo\":{\"totalhits\":10001},\"search\":[" + hit("B") + "]}}"
            );

            assertThrows(WikiLimitReachedException.class, () -> wiki.search("the"));
            assertThat(wiki.sent, hasSize(2));

            wiki.answers.clear();
            wiki.answering("{\"continue\":{\"gsroffset\":50,\"continue\":\"gsroffset||\"},\"query\":{\"searchinfo\":{\"totalhits\":40812},\"pages\":[" + page(1, "A", "a") + "]}}");
            assertThrows(WikiLimitReachedException.class, () -> wiki.searchContents("the"));
            assertThat(wiki.sent, hasSize(3));
        }

        @Test
        @DisplayName("but answering a search counting the most hits the wiki serves, and a read no search generates")
        void searchWithinReach() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki.answering(
                "{\"query\":{\"searchinfo\":{\"totalhits\":10000},\"search\":[" + hit("A") + "]}}",
                fixture("query-titles.json")
            );

            assertThat(wiki.search("x").getTotalHits(), equalTo(Optional.of(10000L)));
            assertThat(wiki.getContents("Dungeon Potion", "God Potion").getPages(), hasSize(2));

            wiki.answering(
                "{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"searchinfo\":{\"totalhits\":2},\"search\":[" + hit("A") + "]}}",
                "{\"query\":{\"searchinfo\":{\"totalhits\":3},\"search\":[" + hit("B") + "]}}"
            );
            assertThat(wiki.search("x").getTotalHits(), equalTo(Optional.of(3L)));
        }

        @Test
        @DisplayName("from a DynamicPageList table whose answer holds no footer, or one the wiki left unfilled, and not from loose wikitext")
        void tableFooter() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;
            String unfilled = "{\"parse\":{\"title\":\"API\",\"text\":\"<table></table><p><span class=\\\"dplquery-rows\\\" data-rows=\\\"%PAGES%\\\" data-total=\\\"%TOTALPAGES%\\\"></span></p>\",\"templates\":[]}}";

            WikiMissingFooterException missing = assertThrows(WikiMissingFooterException.class, () -> wiki.answering(fixture("parse-transclusion.json")).templateTable(DplQuery.uses("Infobox/Power stone")));
            assertThat(missing.getMessage(), containsString("renders '1' DynamicPageList queries and its text holds '0' filled footers"));
            assertThrows(WikiMissingFooterException.class, () -> wiki.answering(unfilled).templateTable(DplQuery.uses("Infobox/Power stone")));
            assertThat(wiki.answering(fixture("parse-transclusion.json")).parseWikitext(DplQuery.uses("Infobox/Power stone").render()).getTableRows(), empty());
        }

        /**
         * {@code Template:Admins} is held by the wiki and used by no page; the wiki answers a query
         * over it as {@code parse-dpl-footers.json} holds for its second query.
         */
        @Test
        @DisplayName("but answering a DynamicPageList table over a template the wiki holds and no page uses, whose footer counts 0 of 0")
        void emptyTable() {
            String none = "{\"parse\":{\"title\":\"API\",\"text\":\"<div class=\\\"mw-parser-output\\\"><p><span class=\\\"dplquery-rows\\\" data-rows=\\\"0\\\" data-total=\\\"0\\\"></span>\\n</p></div>\","
                + "\"templates\":[{\"ns\":10,\"title\":\"Template:Admins\",\"exists\":true}]}}";

            WikiParse parse = SkyBlockWikiContractTest.this.wiki.answering(none).templateTable(DplQuery.uses("Admins").count(5));

            assertThat(parse.getTableRows(), contains(new WikiParse.TableRows(0, 0)));
        }

        /**
         * DynamicPageList answers a query over a template the wiki does not hold with the same
         * {@code 0} of {@code 0} as one over a template no page uses, so the footer alone reads a
         * misspelled, deleted or renamed template as an empty table; the query names its template
         * among {@code templates}, where the wiki marks it missing.
         */
        @Test
        @DisplayName("from a DynamicPageList table over a template the wiki does not hold, whose footer counts 0 of 0 as an unused one's does")
        void missingTemplate() {
            FakeWiki wiki = SkyBlockWikiContractTest.this.wiki;
            DplQuery query = DplQuery.uses("No Such Template Zzq").count(5);

            WikiMissingPageException thrown = assertThrows(WikiMissingPageException.class, () -> wiki.answering(fixture("parse-dpl-missing-template.json")).templateTable(query));
            assertThat(thrown.getMessage(), equalTo("The parse reaches 'Template:No Such Template Zzq', which the wiki does not hold"));
            assertThat(wiki.sent, contains(parameters(WikiRequest.templateTable(query))));
            assertThat(wiki.answering(fixture("parse-dpl-missing-template.json")).parseWikitext(query.render()).getTableRows(), contains(new WikiParse.TableRows(0, 0)));
        }

    }

    @Test
    @DisplayName("refuses a request of another kind than the method sends")
    void wrongKind() {
        assertThrows(IllegalArgumentException.class, () -> this.wiki.parse(WikiRequest.search("x")));
        assertThrows(IllegalArgumentException.class, () -> this.wiki.query(WikiRequest.parsePage("Harp")));
        assertThat(this.wiki.sent, hasSize(0));
    }

    @Test
    @DisplayName("reads a module's source through its title in the module namespace")
    void moduleSource() {
        assertThat(this.wiki.getModuleSource("Pet/Data"), equalTo("-- Module:Pet/Data"));
    }

    /**
     * Sends through the configuration {@link SkyBlockWiki#client()} is built from, assembled as the
     * client assembles it - its decoders, its error decoder, its route interceptor and the rate
     * limit it enforces on each request sent - over a transport that records each request and
     * answers from memory.
     */
    @Nested
    @DisplayName("builds through the client's own configuration")
    class Wire {

        private final @NotNull ClientConfig<SkyBlockWikiContract> config = SkyBlockWiki.config();
        private final @NotNull List<Request> requests = new ArrayList<>();
        private final @NotNull RateLimitManager limiter = new RateLimitManager();
        private final @NotNull Deque<String> bodies = new ArrayDeque<>();
        private int status = 200;
        private @NotNull String body = "{}";
        private final @NotNull SkyBlockWikiContract contract = this.contract();

        private @NotNull SkyBlockWikiContract contract() {
            RouteDiscovery routes = new RouteDiscovery(this.config);
            ResponseCache cache = new ResponseCache(this.config.getTimings().maxCacheBytes(), this.config.getTimings().cacheSafetyFallback());

            return Feign.builder()
                .client(new RateLimitingFeignClient((request, options) -> {
                    this.requests.add(request);
                    return Response.builder()
                        .status(this.status)
                        .reason("Answered")
                        .request(request)
                        .headers(Map.of())
                        .body(this.bodies.isEmpty() ? this.body : this.bodies.removeFirst(), StandardCharsets.UTF_8)
                        .build();
                }, this.limiter, routes))
                .decoder(new InternalResponseDecoder(this.config.getDecoderFactory().apply(this.config.getGson()), cache))
                .errorDecoder(new InternalErrorDecoder(this.config.getErrorDecoder(), routes, cache))
                .requestInterceptor(new InternalRequestInterceptor(this.limiter, routes))
                .doNotCloseAfterDecode()
                .target(new Target.HardCodedTarget<>(SkyBlockWikiContract.class, "https://placeholder"));
        }

        /**
         * Sends every shape through the query map and compares the request line with the map and
         * with the request's URL.
         *
         * <p>
         * Feign writes a parameter whose value is empty as its bare name, {@code gapprefix}, where
         * the URL writes {@code gapprefix=}; the wiki reads both as the empty string.
         */
        @Test
        @DisplayName("a query map it sends as it stands, which decodes to the parameters of the request's URL")
        void queryMap() {
            List<WikiRequest> requests = List.of(
                WikiRequest.getContentsByPrefix("Fairy Souls/List/", 0),
                WikiRequest.getContentsByPrefix("", 0),
                WikiRequest.getContentsByPrefix("Fairy Souls/List/", 0).continuing(Map.of("rvcontinue", "12354|846620")),
                WikiRequest.getContents("Hoppity's Hunt/Rewards", "Dungeon Potion", "A&B?+C", "Caf\u00e9 (x)", "Module:Zone/Data"),
                WikiRequest.getContentsUsingTemplate("Infobox/Reforge stone"),
                WikiRequest.searchContents("insource:\"type=[[Brews]]\""),
                WikiRequest.search("insource:/BUFF\\}\\}/"),
                WikiRequest.queryBucket(BucketQuery.from("power").select("json", "power_stone", "name").where("name", "Hoppity's Hunt").limit(100)),
                WikiRequest.parsePage("Hoppity's Hunt/Rewards", "text", "sections"),
                WikiRequest.transclude("Harp", "Personal_Harp"),
                WikiRequest.moduleSources("Stat/Aliases", "Stat/Data"),
                WikiRequest.templateTable(DplQuery.uses("Infobox/Power stone").include("Infobox/Power stone", "id").table("page", "id").count(200))
            );

            for (WikiRequest request : requests) {
                this.contract.getQuery(request.getParameters());
                String sent = this.requests.getLast().url();

                assertThat(sent, equalTo(WikiRequest.BASE_URL + "/api.php?" + request.getParameters().entrySet().stream()
                    .map(entry -> entry.getValue().isEmpty() ? entry.getKey() : entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"))));
                assertThat(decoded(sent), equalTo(decoded(request.getUrl())));
            }
        }

        @Test
        @DisplayName("a page under /w/, its title encoded once, answered as text, with the User-Agent the wiki asks for")
        void pages() {
            this.body = "<!DOCTYPE html><html lang=\"en\"></html>";

            assertThat(this.contract.getPage("Hoppity's Hunt/Rewards"), equalTo(this.body));
            assertThat(this.requests.getLast().url(), equalTo(WikiRequest.BASE_URL + "/w/Hoppity%27s_Hunt/Rewards"));

            this.contract.getModuleSource("Item/ApiData");
            assertThat(this.requests.getLast().url(), equalTo(WikiRequest.BASE_URL + "/w/Module%3AItem/ApiData?action=raw"));
            assertThat(this.config.getHeaders(), hasEntry("User-Agent", SkyBlockWiki.USER_AGENT));
        }

        @Test
        @DisplayName("a page the wiki does not hold, raised with its status, and a refusal read from JSON the configured decoder binds")
        void failures() {
            this.status = 404;
            this.body = "<!DOCTYPE html><html><title>No Such Page Zzq</title></html>";
            WikiApiException missing = assertThrows(WikiApiException.class, () -> this.contract.getWikitext("No Such Page Zzq"));

            assertThat(missing.getStatus().getCode(), equalTo(404));
            assertThat(missing.getResponse().getError().isPresent(), equalTo(false));

            this.status = 200;
            this.body = fixture("parse-missing-title.json");
            assertThrows(WikiErrorException.class, () -> this.contract.parsePage("No Such Page Zzq"));
        }

        @Test
        @DisplayName("sixty api.php requests a minute, refusing the next before it is sent, and a page route counted apart")
        void rateLimits() {
            Map<String, String> parameters = WikiRequest.search("x").getParameters();

            for (int i = 0; i < 60; i++)
                this.contract.getSearch(parameters);

            assertThrows(RateLimitException.class, () -> this.contract.getSearch(parameters));
            assertThat(this.requests, hasSize(60));

            this.body = "<html></html>";
            this.contract.getPage("Harp");
            assertThat(this.requests, hasSize(61));
        }

        /**
         * Spends the {@code api.php} route's minute on single requests, then reads two parts: the
         * limiter refuses the second before it is sent, and the wait - which opens the limiter's next
         * window rather than sleeping - lets the same part through.
         */
        @Test
        @DisplayName("a paged read the limiter refuses part way, which waits and sends the refused part again")
        void pagedReadWaits() {
            Map<String, String> parameters = WikiRequest.search("x").getParameters();

            for (int i = 0; i < 59; i++)
                this.contract.getSearch(parameters);

            this.bodies.add("{\"continue\":{\"sroffset\":1,\"continue\":\"-||\"},\"query\":{\"search\":[" + hit("A") + "]}}");
            this.bodies.add("{\"query\":{\"search\":[" + hit("B") + "]}}");
            List<Duration> waits = new ArrayList<>();
            WikiPacing pacing = WikiPacing.of(() -> Instant.EPOCH, duration -> {
                waits.add(duration);
                this.limiter.clear();
            }, Duration.ofMinutes(2));

            WikiSearchResult result = this.contract.search(WikiRequest.search("x"), pacing);

            assertThat(result.getHits().stream().map(WikiSearchResult.Hit::getTitle).toList(), contains("A", "B"));
            assertThat(waits, contains(Duration.ofSeconds(1)));
            assertThat(this.requests, hasSize(61));
            assertThat(this.requests.getLast().url(), containsString("sroffset=1"));
        }

        /**
         * Spends the {@code api.php} route's minute on single requests: a parse is refused before it
         * is sent and raises the refusal, while a paged read's first part waits for the limiter's
         * next window and is sent then.
         */
        @Test
        @DisplayName("a paged read started with the minute spent, whose first part waits, where a single request raises")
        void firstPartWaits() {
            Map<String, String> parameters = WikiRequest.search("x").getParameters();

            for (int i = 0; i < 60; i++)
                this.contract.getSearch(parameters);

            assertThrows(RateLimitException.class, () -> this.contract.parsePage("Harp"));
            assertThat(this.requests, hasSize(60));

            this.bodies.add("{\"query\":{\"searchinfo\":{\"totalhits\":1},\"search\":[" + hit("A") + "]}}");
            List<Duration> waits = new ArrayList<>();
            WikiPacing pacing = WikiPacing.of(() -> Instant.EPOCH, duration -> {
                waits.add(duration);
                this.limiter.clear();
            }, Duration.ofMinutes(2));

            WikiSearchResult result = this.contract.search(WikiRequest.search("x"), pacing);

            assertThat(result.getHits().stream().map(WikiSearchResult.Hit::getTitle).toList(), contains("A"));
            assertThat(waits, contains(Duration.ofSeconds(1)));
            assertThat(this.requests, hasSize(61));
            assertThat(this.requests.getLast().url(), containsString("srinfo=totalhits"));
        }

        private static @NotNull Map<String, String> decoded(@NotNull String url) {
            return Arrays.stream(URI.create(url).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                    pair -> pair[0],
                    pair -> pair.length < 2 ? "" : URLDecoder.decode(pair[1], StandardCharsets.UTF_8),
                    (first, second) -> second,
                    LinkedHashMap::new
                ));
        }

    }

    private static @NotNull Map<String, String> parameters(@NotNull WikiRequest request) {
        return new LinkedHashMap<>(request.getParameters());
    }

    private static @NotNull String page(int id, @NotNull String title, String content) {
        String revisions = content == null ? "" : ",\"revisions\":[{\"slots\":{\"main\":{\"contentmodel\":\"wikitext\",\"content\":\"" + content + "\"}}}]";
        return "{\"pageid\":" + id + ",\"ns\":0,\"title\":\"" + title + "\"" + revisions + "}";
    }

    private static @NotNull String hit(@NotNull String title) {
        return "{\"ns\":0,\"title\":\"" + title + "\",\"pageid\":1,\"snippet\":\"\"}";
    }

    /**
     * A clock that stands still until a wait moves it, and the waits spent on it, in order.
     */
    private static final class FakeTime {

        private final @NotNull List<Duration> sleeps = new ArrayList<>();
        private @NotNull Instant now = Instant.parse("2026-09-28T12:00:00Z");

        @NotNull WikiPacing pacing(@NotNull Duration maxWait) {
            return WikiPacing.of(() -> this.now, duration -> {
                this.sleeps.add(duration);
                this.now = this.now.plus(duration);
            }, maxWait);
        }

    }

    /**
     * Answers the contract from queued JSON bodies and records the parameter map of every
     * {@code api.php} request, in the order sent.
     *
     * <p>
     * A queued {@link RateLimitException} is thrown in place of an answer, as the client's limiter
     * refuses a request before sending it; the refused request is recorded apart from the ones sent.
     */
    private static final class FakeWiki implements SkyBlockWikiContract {

        private final @NotNull List<Map<String, String>> sent = new ArrayList<>();
        private final @NotNull List<Map<String, String>> refused = new ArrayList<>();
        private final @NotNull Deque<Object> answers = new ArrayDeque<>();
        private @NotNull Map<String, String> sources = Map.of();

        @NotNull FakeWiki answering(@NotNull Object @NotNull ... answers) {
            this.answers.addAll(Arrays.asList(answers));
            return this;
        }

        @NotNull FakeWiki holding(@NotNull Map<String, String> sources) {
            this.sources = sources;
            return this;
        }

        @Override
        public @NotNull String getPage(@NotNull String title) {
            return "<html>" + WikiRequest.normalize(title) + "</html>";
        }

        @Override
        public @NotNull String getSource(@NotNull String title) {
            return this.sources.getOrDefault(WikiRequest.normalize(title), "-- " + WikiRequest.normalize(title));
        }

        @Override
        public @NotNull WikiParse getParse(@NotNull Map<String, String> parameters) {
            return this.answer(parameters, WikiParse.class);
        }

        @Override
        public @NotNull WikiQueryResult getQuery(@NotNull Map<String, String> parameters) {
            return this.answer(parameters, WikiQueryResult.class);
        }

        @Override
        public @NotNull WikiSearchResult getSearch(@NotNull Map<String, String> parameters) {
            return this.answer(parameters, WikiSearchResult.class);
        }

        @Override
        public @NotNull WikiBucket getBucket(@NotNull Map<String, String> parameters) {
            return this.answer(parameters, WikiBucket.class);
        }

        private <T> @NotNull T answer(@NotNull Map<String, String> parameters, @NotNull Class<T> type) {
            Object answer = this.answers.removeFirst();

            if (answer instanceof RateLimitException refusal) {
                this.refused.add(new LinkedHashMap<>(parameters));
                throw refusal;
            }

            this.sent.add(new LinkedHashMap<>(parameters));
            return GSON.fromJson((String) answer, type);
        }

    }

}
