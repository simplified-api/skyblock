package api.simplified.skyblock.wiki.client;

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
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static api.simplified.skyblock.wiki.WikiFixtures.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers what the guard checks an answer for, chosen by the shape its URL reads as: every URL is one
 * a {@link WikiRequest} renders, or one written by hand in a shape the wiki takes, and every answer
 * is a fixture the wiki gave or one cut to the check.
 */
class WikiResponseGuardTest {

    private static final @NotNull String REFUSAL = fixture("parse-missing-title.json");

    @Nested
    @DisplayName("raises a refusal")
    class Refusals {

        @Test
        @DisplayName("from every api.php shape answered in JSON")
        void everyShape() {
            assertThrows(WikiErrorException.class, () -> check(WikiRequest.parsePage("No Such Page Zzq"), REFUSAL));
            assertThrows(WikiErrorException.class, () -> check(WikiRequest.transclude("Harp"), REFUSAL));
            assertThrows(WikiErrorException.class, () -> check(WikiRequest.getContents("Harp"), REFUSAL));
            assertThrows(WikiErrorException.class, () -> check(WikiRequest.search("x"), REFUSAL));
            assertThrows(WikiErrorException.class, () -> check(WikiRequest.queryBucket(BucketQuery.from("no_such_bucket_zzq").select("a")), fixture("bucket-error.json")));
            assertThrows(WikiErrorException.class, () -> WikiResponseGuard.check(URI.create(WikiRequest.BASE_URL + "/api.php?action=query&meta=siteinfo&format=json"), REFUSAL));
        }

        @Test
        @DisplayName("and passes an answer of another format, which carries its refusal in no form the guard reads")
        void otherFormat() {
            assertDoesNotThrow(() -> WikiResponseGuard.check(URI.create(WikiRequest.BASE_URL + "/api.php?action=parse&page=Harp&format=xml"), "<api><error code=\"missingtitle\"/></api>"));
            assertDoesNotThrow(() -> WikiResponseGuard.check(URI.create(WikiRequest.BASE_URL + "/api.php?action=parse&page=Harp"), "<!DOCTYPE html><html></html>"));
        }

        @Test
        @DisplayName("and fails an answer in JSON that is no JSON object")
        void notJson() {
            assertThrows(JsonParseException.class, () -> check(WikiRequest.getContents("Harp"), ""));
            assertThrows(JsonParseException.class, () -> check(WikiRequest.getContents("Harp"), "<html>"));
        }

    }

    @Nested
    @DisplayName("checks a parse of wikitext as a composite")
    class Composites {

        @Test
        @DisplayName("raising a red link, a template left out and a DynamicPageList result short of its matches")
        void composite() {
            assertThrows(WikiMissingPageException.class, () -> check(WikiRequest.transclude("No Such Page Zzq"), fixture("parse-red-link.json")));
            assertThrows(WikiIncludeSizeException.class, () -> check(WikiRequest.moduleSources("Reforge/Data"), fixture("parse-include-size.json")));
            assertThrows(WikiLimitReachedException.class, () -> check(WikiRequest.templateTable(DplQuery.uses("Infobox/Power stone").count(3)), fixture("parse-dpl-tables.json")));
            assertDoesNotThrow(() -> check(WikiRequest.transclude("Harp", "Personal_Harp"), fixture("parse-templates.json")));
        }

        /**
         * Loose wikitext renders the same URL as a composite of the same text, so it is checked the
         * same way; a parse answering {@code text} alone carries no {@code templates} to read a red
         * link from and is checked for the rest.
         */
        @Test
        @DisplayName("loose wikitext included, for whatever its props answer")
        void looseWikitext() {
            WikiRequest loose = WikiRequest.parseWikitext(WikiText.transclusion("Tyzzo/UI"));

            assertThrows(WikiMissingPageException.class, () -> check(WikiRequest.parseWikitext("{{:No Such Page Zzq}}"), fixture("parse-red-link.json")));
            assertThrows(WikiIncludeSizeException.class, () -> check(loose.withProps("text"), fixture("parse-include-size.json")));
            assertDoesNotThrow(() -> check(loose.withProps("text"), fixture("parse-transclusion.json")));
        }

        /**
         * A parse naming no {@code formatversion=2} is answered in format version 1, where the text is
         * an object and a template names its title under {@code *}, so the composite checks have
         * nothing of the shape they read.
         */
        @Test
        @DisplayName("but a parse answered in format version 1 for its refusal alone")
        void formatVersionOne() {
            URI uri = URI.create(WikiRequest.BASE_URL + "/api.php?action=parse&format=json&prop=text%7Ctemplates&contentmodel=wikitext&text=%7B%7B%3AHarp%7D%7D");
            String answer = "{\"parse\":{\"title\":\"API\",\"pageid\":0,\"text\":{\"*\":\"<p>Harp</p>\"},\"templates\":[{\"ns\":0,\"*\":\"Harp\",\"exists\":\"\"}]}}";

            assertDoesNotThrow(() -> WikiResponseGuard.check(uri, answer));
            assertThrows(WikiErrorException.class, () -> WikiResponseGuard.check(uri, REFUSAL));
        }

        @Test
        @DisplayName("but a parse of a page for its refusal alone, as the contract checks it")
        void page() {
            assertDoesNotThrow(() -> check(WikiRequest.parsePage("Harp", "text", "templates"), fixture("parse-red-link.json")));
            assertDoesNotThrow(() -> check(WikiRequest.parsePage("Harp", "text"), fixture("parse-dpl-tables.json")));
        }

    }

    /**
     * A parse's {@code text} parameter carrying {@link DplQuery}'s footer is a DynamicPageList
     * composite the guard recognises, and its answer holds one filled footer per query or fails.
     */
    @Nested
    @DisplayName("asks a DynamicPageList composite it recognises for a filled footer per query")
    class Footers {

        private final @NotNull String table = DplQuery.uses("Infobox/Power stone").render();

        @Test
        @DisplayName("raising an answer holding no footer, or one the wiki left unfilled, loose wikitext included")
        void missingOrUnfilled() {
            String unfilled = "{\"parse\":{\"title\":\"API\",\"text\":\"<p><span class=\\\"dplquery-rows\\\" data-rows=\\\"3\\\" data-total=\\\"%TOTALPAGES%\\\"></span></p>\",\"templates\":[]}}";

            assertThrows(WikiMissingFooterException.class, () -> check(WikiRequest.templateTable(DplQuery.uses("Infobox/Power stone")), fixture("parse-transclusion.json")));
            assertThrows(WikiMissingFooterException.class, () -> check(WikiRequest.parseWikitext(this.table).withProps("text"), fixture("parse-transclusion.json")));
            assertThrows(WikiMissingFooterException.class, () -> check(WikiRequest.templateTable(DplQuery.uses("Infobox/Power stone")), unfilled));
        }

        @Test
        @DisplayName("reading the answer the wiki gave three queries, one matching nothing, as three footers and one result cut at its count")
        void perQuery() {
            String wikitext = String.join("\n",
                DplQuery.uses("Infobox/Power stone").count(1).render(),
                DplQuery.uses("Admins").count(5).render(),
                DplQuery.uses("Infobox/Power stone").count(3).render()
            );

            WikiLimitReachedException thrown = assertThrows(WikiLimitReachedException.class, () -> check(WikiRequest.parseWikitext(wikitext), fixture("parse-dpl-footers.json")));
            assertThat(thrown.getMessage(), containsString("holds '1' of the '22' pages"));
        }

        /**
         * A query over a template the wiki does not hold counts {@code 0} of {@code 0}, as one over
         * a template no page uses does, and the wiki marks the template it names missing.
         */
        @Test
        @DisplayName("raising a query over a template the wiki does not hold as a red link, though its footer counts 0 of 0")
        void missingTemplate() {
            WikiMissingPageException thrown = assertThrows(WikiMissingPageException.class, () -> check(WikiRequest.templateTable(DplQuery.uses("No Such Template Zzq").count(5)), fixture("parse-dpl-missing-template.json")));

            assertThat(thrown.getMessage(), containsString("'Template:No Such Template Zzq'"));
        }

        @Test
        @DisplayName("passing one whole result per query, and raising fewer footers than the text renders queries")
        void count() {
            String whole = "{\"parse\":{\"title\":\"API\",\"text\":\"<p><span class=\\\"dplquery-rows\\\" data-rows=\\\"22\\\" data-total=\\\"22\\\"></span></p>\",\"templates\":[]}}";

            assertDoesNotThrow(() -> check(WikiRequest.parseWikitext(this.table), whole));
            assertThrows(WikiMissingFooterException.class, () -> check(WikiRequest.parseWikitext(this.table + this.table), whole));
        }

        @Test
        @DisplayName("but not a parse answering no text, nor a DynamicPageList call written without the footer")
        void unrecognised() {
            assertDoesNotThrow(() -> check(WikiRequest.parseWikitext(this.table).withProps("templates"), fixture("parse-templates.json")));
            assertDoesNotThrow(() -> check(WikiRequest.parseWikitext("{{#dpl:uses=Template:Infobox/Power stone|count=5}}"), fixture("parse-transclusion.json")));
        }

    }

    @Nested
    @DisplayName("checks a search whose answer counts the pages it matches")
    class Searches {

        private static final @NotNull String OVER = "{\"batchcomplete\":\"\",\"query\":{\"searchinfo\":{\"totalhits\":40812},\"search\":[]}}";

        @Test
        @DisplayName("raising one matching more pages than the wiki serves hits for, as a list, as a generator and on a later part")
        void overReach() {
            String generated = "{\"batchcomplete\":true,\"query\":{\"searchinfo\":{\"totalhits\":10001},\"pages\":[]}}";

            WikiLimitReachedException thrown = assertThrows(WikiLimitReachedException.class, () -> check(WikiRequest.search("the"), OVER));
            assertThat(thrown.getMessage(), containsString("'40812' pages"));
            assertThrows(WikiLimitReachedException.class, () -> check(WikiRequest.searchContents("the"), generated));
            assertThrows(WikiLimitReachedException.class, () -> check(WikiRequest.search("the").continuing(Map.of("sroffset", "9500", "continue", "-||")), OVER));
        }

        /**
         * MediaWiki counts the pages a search matches unless {@code srinfo} leaves the count out, so a
         * search URL written by hand is checked whenever its answer carries one.
         */
        @Test
        @DisplayName("from a search URL written by hand, whose answer carries the count by default")
        void handWritten() {
            URI uri = URI.create(WikiRequest.BASE_URL + "/api.php?action=query&list=search&srsearch=the&format=json");

            assertThrows(WikiLimitReachedException.class, () -> WikiResponseGuard.check(uri, OVER));
        }

        @Test
        @DisplayName("and passing answers within reach, one carrying no count, and a read no search generates")
        void withinReach() {
            assertDoesNotThrow(() -> check(WikiRequest.search("insource:/BUFF\\}\\}/"), fixture("search.json")));
            assertDoesNotThrow(() -> check(WikiRequest.searchContents("insource:\"type=[[Brews]]\""), fixture("query-search.json")));
            assertDoesNotThrow(() -> check(WikiRequest.search("x"), "{\"query\":{\"search\":[]}}"));
            assertDoesNotThrow(() -> check(WikiRequest.getContents("Dungeon Potion", "God Potion"), fixture("query-titles.json")));
        }

    }

    @Nested
    @DisplayName("checks a Bucket answer against the limit its query parameter runs at")
    class Buckets {

        @Test
        @DisplayName("raising one holding as many rows as the rendered limit, and passing one under it")
        void rendered() {
            assertThrows(WikiLimitReachedException.class, () -> check(WikiRequest.queryBucket(BucketQuery.from("mob_bestiary").limit(2)), fixture("bucket-join.json")));
            assertDoesNotThrow(() -> check(WikiRequest.queryBucket(BucketQuery.from("mob_bestiary").limit(3)), fixture("bucket-join.json")));
        }

        @Test
        @DisplayName("reading a hand-written query with no limit at Bucket's own")
        void handWritten() {
            String rows = "{\"bucketQuery\":\"x\",\"bucket\":[" + "{\"a\":\"1\"},".repeat(BucketQuery.DEFAULT_LIMIT - 1) + "{\"a\":\"1\"}]}";
            URI uri = URI.create(WikiRequest.BASE_URL + "/api.php?action=bucket&query=bucket('power').select('a').run()&format=json");

            assertThrows(WikiLimitReachedException.class, () -> WikiResponseGuard.check(uri, rows));
        }

    }

    @Nested
    @DisplayName("checks a page source for a redirect's stub")
    class Sources {

        @Test
        @DisplayName("under /w/ and through index.php, naming the target, and passes a page's own source")
        void raw() {
            String stub = fixture("raw-redirect.txt");

            WikiRedirectException thrown = assertThrows(WikiRedirectException.class, () -> check(WikiRequest.getWikitext("Hoppity's Collection"), stub));
            assertThat(thrown.getMessage(), containsString("'Hoppity's_Collection' is a redirect to 'Chocolate Factory#Hoppity's Collection'"));
            assertThrows(WikiRedirectException.class, () -> WikiResponseGuard.check(URI.create(WikiRequest.BASE_URL + "/index.php?title=Hoppity%27s_Collection&action=raw"), stub));
            assertDoesNotThrow(() -> check(WikiRequest.getModuleSource("Pet/Data"), "local p = {}\nreturn p"));
        }

        @Test
        @DisplayName("from a module Scribunto stores as a redirect")
        void moduleRedirect() {
            WikiRedirectException thrown = assertThrows(WikiRedirectException.class, () -> check(WikiRequest.getModuleSource("Statname/Data"), fixture("raw-module-redirect.txt")));

            assertThat(thrown.getMessage(), containsString("'Module:Statname/Data' is a redirect to 'Module:Stat/Data'"));
        }

        @Test
        @DisplayName("but not a rendered page, which the wiki answers after following the redirect")
        void rendered() {
            assertDoesNotThrow(() -> check(WikiRequest.getPage("Hoppity's Collection"), fixture("raw-redirect.txt")));
        }

    }

    @Test
    @DisplayName("passes a URL on another host untouched, whatever its answer")
    void otherHost() {
        assertDoesNotThrow(() -> WikiResponseGuard.check(URI.create("https://api.hypixel.net/v2/resources/skyblock/items?format=json&action=parse"), REFUSAL));
        assertDoesNotThrow(() -> WikiResponseGuard.check(URI.create("https://example.com/w/Harp?action=raw"), fixture("raw-redirect.txt")));
    }

    @Test
    @DisplayName("reads the host in any case")
    void hostCase() {
        assertThrows(WikiErrorException.class, () -> WikiResponseGuard.check(URI.create("https://HypixelSkyBlock.Minecraft.Wiki/api.php?action=query&format=json"), REFUSAL));
    }

    private static void check(@NotNull WikiRequest request, @NotNull String body) {
        WikiResponseGuard.check(URI.create(request.getUrl()), body);
    }

}
