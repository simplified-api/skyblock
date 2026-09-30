package api.simplified.skyblock.wiki.request;

import api.simplified.skyblock.wiki.response.WikiParse;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A DynamicPageList query over the pages that use one template, reading template arguments out of
 * each, rendered as
 * {@code {{#dpl:uses=Template:T|include={T}:a:b|table=,...|count=N|resultsfooter=...|noresultsfooter=...}}}
 * followed by {@code {{#if:{{msgnw:Template:T}}|}}}.
 *
 * <p>
 * The wiki walks the pages on its servers, so one parse returns an argument of every page that uses
 * the template - an infobox's {@code id} on every page carrying the infobox. An {@link #include}
 * names a template and the arguments read from its call on each page; a {@link #table} lays the
 * result out as one row per page, the page first and one cell per argument after it. Without a
 * table, each page is one list item.
 *
 * <p>
 * DynamicPageList answers at most a query's count, and at most {@link #MAX_COUNT} whatever the count
 * names, without saying so in the rows. Every query therefore renders a footer, an empty
 * {@code <span class="dplquery-rows" data-rows="R" data-total="T">} after its result, where {@code R}
 * is the number of pages the result holds and {@code T} the number the query matches, and the wiki
 * fills both in. The footer is rendered twice, as {@code resultsfooter} and as
 * {@code noresultsfooter}: DynamicPageList writes the first after a result of one page or more and
 * the second in place of a result of none, {@code 0} of {@code 0}, so every query writes exactly one
 * footer. {@link WikiParse#requireTableRows(int)} asks that each query wrote its footer filled, and
 * {@link WikiParse#requireComplete()} raises a result that holds fewer pages than its query matches.
 *
 * <p>
 * DynamicPageList answers a query over a template the wiki does not hold as it answers one over a
 * template no page uses, {@code 0} of {@code 0}, and does not name the template among the parse's
 * {@code templates}. Every query is therefore followed by {@code {{#if:{{msgnw:Template:T}}|}}},
 * which renders nothing and names {@code Template:T} there with whether the wiki holds it, so
 * {@link WikiParse#requireComplete()} raises a query over a missing template as the red link it is
 * rather than answering an empty result. A template the wiki holds as a redirect reads as held.
 *
 * <p>
 * The parts render in a fixed order - {@code uses}, {@code include}, {@code table}, {@code count},
 * {@code resultsfooter}, {@code noresultsfooter}, then the template's name after the call - whatever
 * order they are named in, and a part never named other than the footers and the name is left out.
 */
public final class DplQuery {

    /**
     * The most pages one query reads: the wiki's DynamicPageList answers a query of a larger count,
     * or of none, with this many pages.
     */
    public static final int MAX_COUNT = 500;

    /**
     * The class of the element every query's footer writes its counts on.
     */
    public static final @NotNull String ROWS_CLASS = "dplquery-rows";

    /**
     * The footer every query renders, which the wiki fills with the pages its result holds and the
     * pages it matches.
     */
    static final @NotNull String FOOTER = "<span class=\"" + ROWS_CLASS + "\" data-rows=\"%PAGES%\" data-total=\"%TOTALPAGES%\"></span>";

    /**
     * The footer as the result of one page or more carries it, once in every query's wikitext.
     */
    private static final @NotNull String RESULTS_FOOTER = "|resultsfooter=" + FOOTER;

    private final @NotNull String template;
    private final @NotNull ConcurrentList<String> includes = Concurrent.newList();
    private final @NotNull ConcurrentList<String> headers = Concurrent.newList();
    private int count;

    private DplQuery(@NotNull String template) {
        this.template = template;
    }

    /**
     * Starts a query over the pages that use a template.
     *
     * @param template the template name, without the {@code Template:} namespace
     * @return the query
     */
    public static @NotNull DplQuery uses(@NotNull String template) {
        return new DplQuery(template);
    }

    /**
     * Reads arguments from the call of a template on each page, rendered {@code {T}:a:b}.
     *
     * <p>
     * Each include adds one part, joined to the ones before it with a comma, so one query can read
     * the template it selects pages by and another template those pages call.
     *
     * @param template the template name, without the {@code Template:} namespace
     * @param arguments the argument names, in the order their cells appear
     * @return this query
     */
    public @NotNull DplQuery include(@NotNull String template, @NotNull String @NotNull ... arguments) {
        StringBuilder part = new StringBuilder("{").append(template).append('}');
        Arrays.stream(arguments).forEach(argument -> part.append(':').append(argument));
        this.includes.add(part.toString());
        return this;
    }

    /**
     * Lays the result out as a table, rendered {@code table=,h1,h2}.
     *
     * <p>
     * The empty first entry leaves the table's class at the wiki's default; the headers that follow
     * name the page column first, then one column per included argument.
     *
     * @param headers the column headers, the page column's first
     * @return this query
     */
    public @NotNull DplQuery table(@NotNull String @NotNull ... headers) {
        this.headers.clear();
        this.headers.addAll(Arrays.asList(headers));
        return this;
    }

    /**
     * Caps the number of pages the query reads, rendered {@code count=N}.
     *
     * <p>
     * A query that names no count reads {@link #MAX_COUNT} pages.
     *
     * @param count the most pages read, at most {@link #MAX_COUNT}
     * @return this query
     * @throws IllegalArgumentException if the count is not positive or is over {@link #MAX_COUNT},
     *     which the wiki would lower in silence
     */
    public @NotNull DplQuery count(int count) {
        if (count < 1 || count > MAX_COUNT)
            throw new IllegalArgumentException(String.format("A query reads between '1' and '%s' pages, not '%s'", MAX_COUNT, count));

        this.count = count;
        return this;
    }

    /**
     * Renders the query as wikitext.
     *
     * @return the parser function call, its two footers last, followed by the call that names its
     *     template among the parse's {@code templates}
     */
    public @NotNull String render() {
        StringBuilder wikitext = new StringBuilder("{{#dpl:uses=")
            .append(WikiText.TEMPLATE_NAMESPACE)
            .append(this.template);

        if (!this.includes.isEmpty())
            wikitext.append("|include=").append(String.join(",", this.includes));

        if (!this.headers.isEmpty())
            wikitext.append("|table=").append(this.headers.stream().collect(Collectors.joining(",", ",", "")));

        if (this.count > 0)
            wikitext.append("|count=").append(this.count);

        return wikitext.append(RESULTS_FOOTER)
            .append("|noresultsfooter=")
            .append(FOOTER)
            .append("}}{{#if:{{msgnw:")
            .append(WikiText.TEMPLATE_NAMESPACE)
            .append(this.template)
            .append("}}|}}")
            .toString();
    }

    /**
     * Counts the queries a wikitext renders, by the footer each carries for a result of one page or
     * more.
     *
     * <p>
     * A DynamicPageList call written without that footer is not counted: its result writes no
     * footer, and nothing reads how many pages it holds.
     *
     * @param wikitext the wikitext a parse renders
     * @return the number of queries
     */
    public static int countIn(@NotNull String wikitext) {
        int queries = 0;

        for (int at = wikitext.indexOf(RESULTS_FOOTER); at >= 0; at = wikitext.indexOf(RESULTS_FOOTER, at + RESULTS_FOOTER.length()))
            queries++;

        return queries;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String toString() {
        return this.render();
    }

}
