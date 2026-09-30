package api.simplified.skyblock.wiki.request;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers what a {@link WikiRequest} renders: the URL of every shape, character for character against
 * requests the wiki has answered, the parameter map the contract sends, the one title encoding both
 * share, the wikitext and Bucket compositions, and the requests a factory refuses.
 *
 * <p>
 * Where a shape renders more than the URL the wiki answered, the test keeps that URL as it was
 * answered and adds exactly the difference: {@code redirects=1} before {@code format} on a shape that
 * names a title, {@code gapfilterredir=nonredirects} after {@code gaplimit} on a prefix read,
 * {@code srinfo=totalhits} or {@code gsrinfo=totalhits} on a search, or a {@link DplQuery}'s footers
 * before its closing braces and the call naming its template after them. The wiki answers every one
 * of them.
 */
class WikiRequestTest {

    private static final @NotNull String API = "https://hypixelskyblock.minecraft.wiki/api.php?";

    /**
     * The two footers every {@link DplQuery} renders, as the wikitext carries them.
     */
    private static final @NotNull String FOOTER = "|resultsfooter=<span class=\"dplquery-rows\" data-rows=\"%PAGES%\" data-total=\"%TOTALPAGES%\"></span>"
        + "|noresultsfooter=<span class=\"dplquery-rows\" data-rows=\"%PAGES%\" data-total=\"%TOTALPAGES%\"></span>";

    /**
     * The footers as a parse's {@code text} parameter carries them in the URL.
     */
    private static final @NotNull String ENCODED_FOOTER = "%7Cresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E"
        + "%7Cnoresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E";

    /**
     * Writes the call that follows every {@link DplQuery}, which renders nothing and names the
     * query's template among the parse's {@code templates}, as the wikitext carries it.
     */
    private static @NotNull String templateName(@NotNull String template) {
        return "{{#if:{{msgnw:Template:" + template + "}}|}}";
    }

    /**
     * Adds {@code redirects=1} to a URL the wiki answered without it, where every shape that names a
     * title renders it: immediately before {@code format}.
     */
    private static @NotNull String followingRedirects(@NotNull String answered) {
        return answered.replace("&format=json", "&redirects=1&format=json");
    }

    @Nested
    @DisplayName("renders, character for character, a URL the wiki has answered")
    class AnsweredUrls {

        /**
         * The wiki answered this read without {@code gapfilterredir}; the shape leaves the redirects
         * under the prefix out, since the wiki refuses {@code redirects=1} on the {@code allpages}
         * generator, so it renders the answered URL with that one parameter added.
         */
        @Test
        @DisplayName("an allpages prefix read, leaving redirects out")
        void prefix() {
            String answered = API + "action=query&generator=allpages&gapprefix=Fairy_Souls/List/&gapnamespace=0&gaplimit=max&prop=revisions&rvprop=content&rvslots=main&format=json&formatversion=2";

            assertThat(
                WikiRequest.getContentsByPrefix("Fairy_Souls/List/", 0).getUrl(),
                equalTo(answered.replace("&gaplimit=max", "&gaplimit=max&gapfilterredir=nonredirects"))
            );
        }

        /**
         * The wiki answered this read without {@code redirects=1}; the shape asks the wiki to answer
         * a title naming a redirect as the page it leads to, so it renders the answered URL with that
         * one parameter added.
         */
        @Test
        @DisplayName("a read of named titles, following redirects")
        void titles() {
            assertThat(
                WikiRequest.getContents("Dungeon_Potion", "God_Potion").getUrl(),
                equalTo(followingRedirects(API + "action=query&prop=revisions&rvprop=content&rvslots=main&titles=Dungeon_Potion%7CGod_Potion&format=json&formatversion=2"))
            );
        }

        /**
         * The wiki answered this read without {@code redirects=1} or {@code gsrinfo=totalhits}. The
         * shape follows redirects as every read of page sources does, and asks each part to count
         * the pages the search matches, since the wiki serves no hit past the
         * {@link WikiRequest#MAX_SEARCH_HITS}th and the count is the only sign of a search matching
         * more; so it renders the answered URL with those two parameters added. The wiki answered it
         * with {@code query.searchinfo.totalhits} beside the pages.
         */
        @Test
        @DisplayName("a read of the pages a search generates, following redirects and counting the pages it matches")
        void generatorSearch() {
            String answered = API + "action=query&generator=search&gsrsearch=insource%3A%22type%3D%5B%5BBrews%5D%5D%22&gsrlimit=50&prop=revisions&rvprop=content&rvslots=main&format=json&formatversion=2";

            assertThat(
                WikiRequest.searchContents("insource:\"type=[[Brews]]\"").getUrl(),
                equalTo(followingRedirects(answered).replace("&gsrlimit=50", "&gsrlimit=50&gsrinfo=totalhits"))
            );
        }

        /**
         * The wiki answered this search without {@code srinfo=totalhits}. The shape asks each part to
         * count the pages the search matches, since the wiki serves no hit past the
         * {@link WikiRequest#MAX_SEARCH_HITS}th and the count is the only sign of a search matching
         * more, so it renders the answered URL with that one parameter added before
         * {@code srlimit}. The wiki answered it with {@code query.searchinfo.totalhits}.
         */
        @Test
        @DisplayName("a search, counting the pages it matches")
        void search() {
            String answered = API + "action=query&list=search&srsearch=insource%3A%2FBUFF%5C%7D%5C%7D%2F&srprop=snippet&srlimit=500&format=json";

            assertThat(
                WikiRequest.search("insource:/BUFF\\}\\}/").getUrl(),
                equalTo(answered.replace("&srlimit=500", "&srinfo=totalhits&srlimit=500"))
            );
        }

        /**
         * The wiki answered this parse with one footer per query: {@code 1} of {@code 22} for the
         * first, {@code 0} of {@code 0} written by the second's {@code noresultsfooter} over
         * {@code Template:Admins}, which the wiki holds and no page uses, and {@code 3} of {@code 22}
         * for the third; it named both templates among {@code templates} as held, as
         * {@code parse-dpl-footers.json} holds.
         */
        @Test
        @DisplayName("loose wikitext of DynamicPageList queries, each with both footers and its template's name, one matching nothing")
        void dplFooters() {
            String wikitext = String.join("\n",
                DplQuery.uses("Infobox/Power stone").count(1).render(),
                DplQuery.uses("Admins").count(5).render(),
                DplQuery.uses("Infobox/Power stone").count(3).render()
            );

            assertThat(
                WikiRequest.parseWikitext(wikitext).getUrl(),
                equalTo(API + "action=parse&contentmodel=wikitext&prop=text%7Ctemplates&disablelimitreport=1&format=json&formatversion=2&text=%7B%7B%23dpl%3Auses%3DTemplate%3AInfobox%2FPower%20stone%7Ccount%3D1%7Cresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7Cnoresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7D%7D%7B%7B%23if%3A%7B%7Bmsgnw%3ATemplate%3AInfobox%2FPower%20stone%7D%7D%7C%7D%7D%0A%7B%7B%23dpl%3Auses%3DTemplate%3AAdmins%7Ccount%3D5%7Cresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7Cnoresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7D%7D%7B%7B%23if%3A%7B%7Bmsgnw%3ATemplate%3AAdmins%7D%7D%7C%7D%7D%0A%7B%7B%23dpl%3Auses%3DTemplate%3AInfobox%2FPower%20stone%7Ccount%3D3%7Cresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7Cnoresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7D%7D%7B%7B%23if%3A%7B%7Bmsgnw%3ATemplate%3AInfobox%2FPower%20stone%7D%7D%7C%7D%7D")
            );
        }

        /**
         * The wiki answered this parse with {@code 0} of {@code 0}, as it answers a query over a
         * template no page uses, and named the template among {@code templates} as not held, as
         * {@code parse-dpl-missing-template.json} holds.
         */
        @Test
        @DisplayName("a DynamicPageList table over a template the wiki does not hold")
        void dplMissingTemplate() {
            assertThat(
                WikiRequest.templateTable(DplQuery.uses("No Such Template Zzq").count(5)).getUrl(),
                equalTo(API + "action=parse&contentmodel=wikitext&prop=text%7Ctemplates&disablelimitreport=1&format=json&formatversion=2&text=%7B%7B%23dpl%3Auses%3DTemplate%3ANo%20Such%20Template%20Zzq%7Ccount%3D5%7Cresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7Cnoresultsfooter%3D%3Cspan%20class%3D%22dplquery-rows%22%20data-rows%3D%22%25PAGES%25%22%20data-total%3D%22%25TOTALPAGES%25%22%3E%3C%2Fspan%3E%7D%7D%7B%7B%23if%3A%7B%7Bmsgnw%3ATemplate%3ANo%20Such%20Template%20Zzq%7D%7D%7C%7D%7D")
            );
        }

        @Test
        @DisplayName("a Bucket query joining a second bucket")
        void bucketJoin() {
            BucketQuery query = BucketQuery.from("mob_bestiary")
                .join("mob_stats", "mob_bestiary.page_name", "mob_stats.page_name")
                .select(
                    "mob_bestiary.page_name", "mob_bestiary.family", "mob_bestiary.name", "mob_bestiary.main",
                    "mob_bestiary.section", "mob_bestiary.lore", "mob_bestiary.bracket", "mob_bestiary.max_tier",
                    "mob_stats.mob", "mob_stats.levels", "mob_stats.mob_types", "mob_stats.mob_types_text"
                )
                .limit(5000);

            assertThat(
                WikiRequest.queryBucket(query).getUrl(),
                equalTo(API + "action=bucket&query=bucket('mob_bestiary').join('mob_stats','mob_bestiary.page_name','mob_stats.page_name').select('mob_bestiary.page_name','mob_bestiary.family','mob_bestiary.name','mob_bestiary.main','mob_bestiary.section','mob_bestiary.lore','mob_bestiary.bracket','mob_bestiary.max_tier','mob_stats.mob','mob_stats.levels','mob_stats.mob_types','mob_stats.mob_types_text').limit(5000).run()&format=json&utf8=1")
            );
        }

        @Test
        @DisplayName("loose wikitext transcluding two pages, answering the rendered text alone")
        void transclusion() {
            assertThat(
                WikiRequest.parseWikitext(WikiText.transclusion("Harp") + WikiText.transclusion("Personal_Harp")).withProps("text").getUrl(),
                equalTo(API + "action=parse&contentmodel=wikitext&prop=text&disablelimitreport=1&format=json&formatversion=2&text=%7B%7B%3AHarp%7D%7D%7B%7B%3APersonal_Harp%7D%7D")
            );
        }

        @Test
        @DisplayName("loose wikitext transcluding a module page")
        void transclusionOfModule() {
            String wikitext = WikiText.transclusion("Events") + WikiText.transclusion("Dante") + WikiText.transclusion("Module:Mayor/Data");

            assertThat(
                WikiRequest.parseWikitext(wikitext).withProps("text").getUrl(),
                equalTo(API + "action=parse&contentmodel=wikitext&prop=text&disablelimitreport=1&format=json&formatversion=2&text=%7B%7B%3AEvents%7D%7D%7B%7B%3ADante%7D%7D%7B%7B%3AModule%3AMayor%2FData%7D%7D")
            );
        }

        /**
         * The wiki answered this parse with lists that carry no footer; every {@link DplQuery}
         * renders two and names its template after its call, so the URL is the answered one with
         * the footers before each list's closing braces and the name after them.
         */
        @Test
        @DisplayName("loose wikitext wrapping DynamicPageList lists and transclusions, each list with its footer and its template's name")
        void looseComposite() {
            String list = DplQuery.uses("LiveCountdownBox").include("LiveCountdownBox", "routine").count(500).render();
            String wikitext = "<div class=\"dplrows\">" + list + "</div>"
                + WikiText.transclusion("Events") + WikiText.transclusion("Dante") + WikiText.transclusion("Module:Mayor/Data")
                + "<div class=\"dpljoin\">" + list + "</div>";
            String answered = API + "action=parse&contentmodel=wikitext&prop=text&disablelimitreport=1&format=json&formatversion=2&text=%3Cdiv%20class%3D%22dplrows%22%3E%7B%7B%23dpl%3Auses%3DTemplate%3ALiveCountdownBox%7Cinclude%3D%7BLiveCountdownBox%7D%3Aroutine%7Ccount%3D500%7D%7D%3C%2Fdiv%3E%7B%7B%3AEvents%7D%7D%7B%7B%3ADante%7D%7D%7B%7B%3AModule%3AMayor%2FData%7D%7D%3Cdiv%20class%3D%22dpljoin%22%3E%7B%7B%23dpl%3Auses%3DTemplate%3ALiveCountdownBox%7Cinclude%3D%7BLiveCountdownBox%7D%3Aroutine%7Ccount%3D500%7D%7D%3C%2Fdiv%3E";

            assertThat(
                WikiRequest.parseWikitext(wikitext).withProps("text").getUrl(),
                equalTo(answered.replace(
                    "%7Ccount%3D500%7D%7D",
                    "%7Ccount%3D500" + ENCODED_FOOTER + "%7D%7D%7B%7B%23if%3A%7B%7Bmsgnw%3ATemplate%3ALiveCountdownBox%7D%7D%7C%7D%7D"
                ))
            );
        }

        @Test
        @DisplayName("a rendered page and a page source")
        void pages() {
            assertThat(WikiRequest.getPage("Essence Shops/Spider").getUrl(), equalTo("https://hypixelskyblock.minecraft.wiki/w/Essence_Shops/Spider"));
            assertThat(WikiRequest.getModuleSource("Pet/Data").getUrl(), equalTo("https://hypixelskyblock.minecraft.wiki/w/Module:Pet/Data?action=raw"));
            assertThat(WikiRequest.getWikitext("Dctr._Paper").getUrl(), equalTo("https://hypixelskyblock.minecraft.wiki/w/Dctr._Paper?action=raw"));
            assertThat(WikiRequest.getPage("Hoppity's_Hunt").getUrl(), equalTo("https://hypixelskyblock.minecraft.wiki/w/Hoppity%27s_Hunt"));
        }

    }

    @Nested
    @DisplayName("encodes a title one way wherever it goes")
    class Titles {

        @Test
        @DisplayName("normalizes spaces and underscores to single underscores, trimmed")
        void normalizes() {
            assertThat(WikiRequest.normalize("  Fairy  Souls__List _"), equalTo("Fairy_Souls_List"));
            assertThat(WikiRequest.normalize("Module:Item/ApiData"), equalTo("Module:Item/ApiData"));
        }

        @Test
        @DisplayName("keeps / and : readable and percent-encodes everything else outside the unreserved characters")
        void encodes() {
            assertThat(WikiRequest.getPage("Hoppity's Hunt/Rewards").getUrl(), endsWith("/w/Hoppity%27s_Hunt/Rewards"));
            assertThat(WikiRequest.getPage("Caf\u00e9 (x)").getUrl(), endsWith("/w/Caf%C3%A9_%28x%29"));
            assertThat(WikiRequest.getContents("A&B?+C", "Module:Zone/Data").getUrl(), containsString("&titles=A%26B%3F%2BC%7CModule:Zone/Data&"));
            assertThat(WikiRequest.getContentsByPrefix("Essence Shops/", 0).getUrl(), containsString("&gapprefix=Essence_Shops/&"));
        }

        @Test
        @DisplayName("names a template by its namespace")
        void template() {
            assertThat(WikiRequest.getContentsUsingTemplate("Infobox/Reforge stone").getUrl(), equalTo(
                API + "action=query&generator=embeddedin&geititle=Template:Infobox/Reforge_stone&geilimit=50&prop=revisions&rvprop=content&rvslots=main&redirects=1&format=json&formatversion=2"
            ));
        }

    }

    @Nested
    @DisplayName("renders the parameter map the contract sends")
    class Parameters {

        @Test
        @DisplayName("in the order the URL carries them")
        void ordered() {
            assertThat(
                WikiRequest.getContentsByPrefix("Fairy Souls/List/", 0).getParameters().keySet(),
                contains("action", "generator", "gapprefix", "gapnamespace", "gaplimit", "gapfilterredir", "prop", "rvprop", "rvslots", "format", "formatversion")
            );
        }

        @Test
        @DisplayName("with every value encoded outside the unreserved characters, readable parameters included")
        void strict() {
            assertThat(WikiRequest.getContentsByPrefix("Fairy Souls/List/", 0).getParameters().get("gapprefix"), equalTo("Fairy_Souls%2FList%2F"));
            assertThat(WikiRequest.getContents("Hoppity's Hunt/Rewards").getParameters().get("titles"), equalTo("Hoppity%27s_Hunt%2FRewards"));
            assertThat(
                WikiRequest.queryBucket(BucketQuery.from("power").select("name").limit(5)).getParameters().get("query"),
                equalTo("bucket%28%27power%27%29.select%28%27name%27%29.limit%285%29.run%28%29")
            );
            assertThat(WikiRequest.parseWikitext("{{:Harp}}").getParameters().get("prop"), equalTo("text%7Ctemplates"));
        }

    }

    @Nested
    @DisplayName("composes wikitext")
    class Compositions {

        @Test
        @DisplayName("module sources and DynamicPageList tables, as a composite the wiki has answered")
        void moduleAndTables() {
            String composite = String.join("\n",
                WikiText.moduleSource("Reforge/Data"),
                DplQuery.uses("Infobox/Reforge stone").include("Infobox/Reforge stone", "reforge_name", "id", "req_skill_level").table("page", "reforge", "id", "level").count(500).render(),
                DplQuery.uses("Infobox/Weapon").include("Infobox/Weapon", "id").table("page", "id").count(500).render(),
                WikiText.moduleSource("Stat/Aliases"),
                WikiText.moduleSource("Stat/Data")
            );

            assertThat(composite, equalTo("""
                {{#tag:pre|{{msgnw:Module:Reforge/Data}}}}
                {{#dpl:uses=Template:Infobox/Reforge stone|include={Infobox/Reforge stone}:reforge_name:id:req_skill_level|table=,page,reforge,id,level|count=500%1$s}}%2$s
                {{#dpl:uses=Template:Infobox/Weapon|include={Infobox/Weapon}:id|table=,page,id|count=500%1$s}}%3$s
                {{#tag:pre|{{msgnw:Module:Stat/Aliases}}}}
                {{#tag:pre|{{msgnw:Module:Stat/Data}}}}""".formatted(FOOTER, templateName("Infobox/Reforge stone"), templateName("Infobox/Weapon"))));
        }

        @Test
        @DisplayName("a page source and a table reading two templates")
        void pageSourceAndTwoIncludes() {
            assertThat(WikiText.pageSource("Maxwell/UI"), equalTo("{{#tag:pre|{{msgnw::Maxwell/UI}}}}"));
            assertThat(
                DplQuery.uses("Infobox/Power stone")
                    .count(200)
                    .table("page", "id", "power", "presets")
                    .include("Infobox/Power stone", "id", "power_name")
                    .include("Calculator:Power Stone Table", "presets")
                    .render(),
                equalTo("{{#dpl:uses=Template:Infobox/Power stone|include={Infobox/Power stone}:id:power_name,{Calculator:Power Stone Table}:presets|table=,page,id,power,presets|count=200" + FOOTER + "}}"
                    + templateName("Infobox/Power stone"))
            );
        }

        @Test
        @DisplayName("a query naming no part but its template, which still renders its footers and its template's name")
        void bareDpl() {
            assertThat(
                DplQuery.uses("Infobox/Weapon").render(),
                equalTo("{{#dpl:uses=Template:Infobox/Weapon"
                    + "|resultsfooter=<span class=\"dplquery-rows\" data-rows=\"%PAGES%\" data-total=\"%TOTALPAGES%\"></span>"
                    + "|noresultsfooter=<span class=\"dplquery-rows\" data-rows=\"%PAGES%\" data-total=\"%TOTALPAGES%\"></span>}}"
                    + "{{#if:{{msgnw:Template:Infobox/Weapon}}|}}")
            );
        }

        @Test
        @DisplayName("the queries a wikitext renders, counted by their footer, and not a call written without it")
        void countQueries() {
            String query = DplQuery.uses("Infobox/Weapon").include("Infobox/Weapon", "id").render();
            String handWritten = "{{#dpl:uses=Template:Infobox/Weapon|resultsfooter=%TOTALPAGES%}}";

            assertThat(DplQuery.countIn(query + WikiText.transclusion("Harp") + query + handWritten), equalTo(2));
            assertThat(DplQuery.countIn(handWritten), equalTo(0));
            assertThat(DplQuery.countIn(""), equalTo(0));
        }

        @Test
        @DisplayName("a list without a table, and the module sources a request composes")
        void listAndRequests() {
            assertThat(
                DplQuery.uses("LiveCountdownBox").include("LiveCountdownBox", "routine").count(500).render(),
                equalTo("{{#dpl:uses=Template:LiveCountdownBox|include={LiveCountdownBox}:routine|count=500" + FOOTER + "}}" + templateName("LiveCountdownBox"))
            );
            assertThat(
                WikiRequest.moduleSources("Stat/Aliases", "Stat/Data").getUrl(),
                endsWith("&text=%7B%7B%23tag%3Apre%7C%7B%7Bmsgnw%3AModule%3AStat%2FAliases%7D%7D%7D%7D%7B%7B%23tag%3Apre%7C%7B%7Bmsgnw%3AModule%3AStat%2FData%7D%7D%7D%7D")
            );
        }

        @Test
        @DisplayName("a Bucket query in its fixed order, escaping quotes, whatever order its parts are named in")
        void bucket() {
            BucketQuery query = BucketQuery.from("power")
                .limit(100)
                .where("name", "Hoppity's Hunt")
                .select("json", "power_stone", "name");

            assertThat(query.render(), equalTo("bucket('power').select('json','power_stone','name').where('name','Hoppity\\'s Hunt').limit(100).run()"));
            assertThat(WikiRequest.queryBucket(query).getUrl(), containsString(".where('name','Hoppity%5C's%20Hunt')."));
        }

        @Test
        @DisplayName("a Bucket query naming no limit at the one Bucket runs it at, rendered")
        void bucketDefaultLimit() {
            BucketQuery query = BucketQuery.from("power").select("name");

            assertThat(query.getLimit(), equalTo(BucketQuery.DEFAULT_LIMIT));
            assertThat(query.render(), equalTo("bucket('power').select('name').limit(500).run()"));
        }

    }

    @Nested
    @DisplayName("reads back the limit a Bucket query runs at")
    class BucketLimits {

        @Test
        @DisplayName("from the query as rendered, and from the last limit a hand-written one calls")
        void rendered() {
            assertThat(BucketQuery.limitOf(BucketQuery.from("power").limit(100).render()), equalTo(100));
            assertThat(BucketQuery.limitOf(BucketQuery.from("power").render()), equalTo(BucketQuery.DEFAULT_LIMIT));
            assertThat(BucketQuery.limitOf("bucket('power').limit(10).select('name').limit( 20 ).run()"), equalTo(20));
        }

        @Test
        @DisplayName("as Bucket runs it: none or none positive at its default, and one over the most it answers at that")
        void asBucketRunsIt() {
            assertThat(BucketQuery.limitOf("bucket('power').select('name').run()"), equalTo(BucketQuery.DEFAULT_LIMIT));
            assertThat(BucketQuery.limitOf("bucket('power').limit(0).run()"), equalTo(BucketQuery.DEFAULT_LIMIT));
            assertThat(BucketQuery.limitOf("bucket('power').limit(9000).run()"), equalTo(BucketQuery.MAX_LIMIT));
            assertThat(BucketQuery.limitOf("bucket('power').limit(99999999999999999999).run()"), equalTo(BucketQuery.MAX_LIMIT));
        }

        @Test
        @DisplayName("skipping a limit a string quotes, escaped quotes included")
        void quoted() {
            BucketQuery query = BucketQuery.from("power").where("name", "it's .limit(3)").limit(40);

            assertThat(BucketQuery.limitOf(query.render()), equalTo(40));
            assertThat(BucketQuery.limitOf("bucket('power').where('name',\".limit(3)\").run()"), equalTo(BucketQuery.DEFAULT_LIMIT));
        }

    }

    @Test
    @DisplayName("asks the wiki to follow redirects on every shape naming a title that can follow one, and on no other")
    void redirects() {
        List<WikiRequest> following = List.of(
            WikiRequest.parsePage("Harp"),
            WikiRequest.getContents("Harp"),
            WikiRequest.searchContents("Harp"),
            WikiRequest.getContentsUsingTemplate("Infobox/Weapon")
        );
        List<WikiRequest> others = List.of(
            WikiRequest.search("Harp"),
            WikiRequest.queryBucket(BucketQuery.from("power")),
            WikiRequest.parseWikitext("{{:Harp}}"),
            WikiRequest.transclude("Harp"),
            WikiRequest.getPage("Harp"),
            WikiRequest.getWikitext("Harp")
        );

        following.forEach(request -> assertThat(request.getUrl(), containsString("&redirects=1&format=json&")));
        others.forEach(request -> assertThat(request.getUrl(), not(containsString("redirects"))));
        assertThat(WikiRequest.parsePage("Harp", "text", "sections").getUrl(), equalTo(
            API + "action=parse&page=Harp&prop=text%7Csections&disablelimitreport=1&redirects=1&format=json&formatversion=2"
        ));
    }

    /**
     * The wiki answers {@code redirects=1} beside {@code generator=allpages} with the error
     * {@code params}, "Use gapfilterredir=nonredirects instead of redirects when using allpages as
     * a generator", so a prefix read that carried it would fail on every call.
     */
    @Test
    @DisplayName("leaves redirects out of a prefix read, which the wiki refuses to follow them on")
    void prefixRedirects() {
        WikiRequest prefix = WikiRequest.getContentsByPrefix("Hoppity's C", 0);

        assertThat(prefix.getUrl(), not(containsString("&redirects=")));
        assertThat(prefix.getUrl(), containsString("&gaplimit=max&gapfilterredir=nonredirects&prop=revisions&"));
        assertThat(prefix.getParameters(), not(hasKey("redirects")));
        assertThat(prefix.continuing(Map.of("gapcontinue", "Hoppity's_Collection", "continue", "gapcontinue||")).getUrl(), not(containsString("&redirects=")));
    }

    @Nested
    @DisplayName("refuses")
    class Refusals {

        @Test
        @DisplayName("a request whose URL outgrows what the wiki accepts, and takes one that just fits")
        void length() {
            int base = WikiRequest.parseWikitext("").getUrl().length();

            assertThat(WikiRequest.parseWikitext("a".repeat(WikiRequest.MAX_URL_LENGTH - base)).getUrl().length(), equalTo(WikiRequest.MAX_URL_LENGTH));
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.parseWikitext("a".repeat(WikiRequest.MAX_URL_LENGTH - base + 1)));
        }

        @Test
        @DisplayName("measuring the length as the contract sends it, every reserved character encoded")
        void strictLength() {
            BucketQuery query = BucketQuery.from("x").select(IntStream.range(0, 1000).mapToObj(i -> "a").toArray(String[]::new));
            int readable = WikiRequest.queryBucket(BucketQuery.from("x")).getUrl().length() + query.render().length();

            assertThat(readable < WikiRequest.MAX_URL_LENGTH, equalTo(true));
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.queryBucket(query));
        }

        /**
         * The contract sends a {@code /w/} title encoded everywhere but {@code /}, so a {@code :} the
         * URL keeps readable goes out as {@code %3A}.
         */
        @Test
        @DisplayName("measuring a page's path as the contract sends it, every colon encoded")
        void strictPathLength() {
            String title = "a:".repeat(2700);

            assertThat(WikiRequest.BASE_URL.length() + "/w/".length() + title.length() < WikiRequest.MAX_URL_LENGTH, equalTo(true));
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.getPage(title));
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.getWikitext(title));
        }

        @Test
        @DisplayName("a read of no titles or of more than one query takes")
        void titleCount() {
            String[] tooMany = IntStream.rangeClosed(0, WikiRequest.MAX_TITLES).mapToObj(i -> "Page " + i).toArray(String[]::new);

            assertThrows(IllegalArgumentException.class, WikiRequest::getContents);
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.getContents(tooMany));
        }

        @Test
        @DisplayName("props on a composite that leave out what its check reads")
        void compositeProps() {
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.transclude("Harp", "Personal_Harp").withProps("text"));
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.moduleSources("Stat/Data").withProps("templates"));
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.templateTable(DplQuery.uses("X")).withProps("text", "sections"));
        }

        @Test
        @DisplayName("an empty composite, props on a request that is no parse, and a continued page")
        void shapes() {
            assertThrows(IllegalArgumentException.class, WikiRequest::transclude);
            assertThrows(IllegalArgumentException.class, WikiRequest::moduleSources);
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.search("x").withProps("text"));
            assertThrows(IllegalArgumentException.class, () -> WikiRequest.getPage("Harp").continuing(Map.of("continue", "-||")));
            assertThrows(IllegalArgumentException.class, () -> DplQuery.uses("X").count(0));
            assertThrows(IllegalArgumentException.class, () -> BucketQuery.from("x").limit(0));
        }

        @Test
        @DisplayName("a count or a limit over the most the wiki answers, which it would lower in silence, and takes the most")
        void overTheMost() {
            assertThrows(IllegalArgumentException.class, () -> DplQuery.uses("X").count(DplQuery.MAX_COUNT + 1));
            assertThrows(IllegalArgumentException.class, () -> BucketQuery.from("x").limit(BucketQuery.MAX_LIMIT + 1));
            assertThat(DplQuery.uses("X").count(DplQuery.MAX_COUNT).render(), containsString("|count=500|"));
            assertThat(BucketQuery.from("x").limit(BucketQuery.MAX_LIMIT).render(), endsWith(".limit(5000).run()"));
        }

    }

    @Test
    @DisplayName("marks the three composites, answering text and templates, and keeps the mark through other props")
    void composites() {
        WikiRequest composite = WikiRequest.transclude("Harp", "Personal_Harp");

        assertThat(composite.isComposite(), equalTo(true));
        assertThat(WikiRequest.moduleSources("Stat/Data").isComposite(), equalTo(true));
        assertThat(WikiRequest.templateTable(DplQuery.uses("X")).isComposite(), equalTo(true));
        assertThat(WikiRequest.parseWikitext(WikiText.transclusion("Harp")).isComposite(), equalTo(false));
        assertThat(WikiRequest.parsePage("Harp").isComposite(), equalTo(false));
        assertThat(composite.withProps("sections", "templates", "text").isComposite(), equalTo(true));
        assertThat(composite.getUrl(), equalTo(
            API + "action=parse&contentmodel=wikitext&prop=text%7Ctemplates&disablelimitreport=1&format=json&formatversion=2&text=%7B%7B%3AHarp%7D%7D%7B%7B%3APersonal_Harp%7D%7D"
        ));
    }

    @Test
    @DisplayName("continues a request by setting each continuation parameter, in place when it already carries it")
    void continuing() {
        WikiRequest first = WikiRequest.search("insource:/BUFF\\}\\}/");
        WikiRequest next = first.continuing(Map.of("sroffset", "500"));

        assertThat(next.getUrl(), endsWith("&srlimit=500&format=json&sroffset=500"));
        assertThat(next.continuing(Map.of("sroffset", "1000")).getUrl(), endsWith("&format=json&sroffset=1000"));
        assertThat(first.getUrl(), endsWith("&format=json"));
    }

}
