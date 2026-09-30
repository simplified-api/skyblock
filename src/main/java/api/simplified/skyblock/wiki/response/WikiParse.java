package api.simplified.skyblock.wiki.response;

import api.simplified.skyblock.wiki.exception.WikiIncludeSizeException;
import api.simplified.skyblock.wiki.exception.WikiLimitReachedException;
import api.simplified.skyblock.wiki.exception.WikiMissingFooterException;
import api.simplified.skyblock.wiki.exception.WikiMissingPageException;
import api.simplified.skyblock.wiki.request.DplQuery;
import api.simplified.skyblock.wiki.request.WikiRequest;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.gson.annotation.SerializedPath;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The wiki's answer to a parse, {@code action=parse} in format version 2: the rendered HTML and
 * whichever other props the request asked for.
 *
 * <p>
 * A composite parse - several documents rendered into one - can succeed while missing part of what
 * it names, and {@link #requireComplete()} is the check for each of the three ways it does.
 * {@link #requireTableRows(int)} asks that every {@link DplQuery} result wrote the footer that check
 * reads its counts from.
 */
@Getter
public class WikiParse extends WikiResponse {

    /**
     * The opening tag of the footer a {@link DplQuery} renders, as the wiki writes it into the
     * rendered text, whatever its counts hold.
     */
    private static final @NotNull Pattern FOOTER_TAG = Pattern.compile("<span class=\"" + DplQuery.ROWS_CLASS + "\"[^>]*>");

    /**
     * The opening tag of a footer the wiki filled with both counts.
     */
    private static final @NotNull Pattern FILLED_FOOTER = Pattern.compile(
        "<span class=\"" + DplQuery.ROWS_CLASS + "\" data-rows=\"(\\d+)\" data-total=\"(\\d+)\">"
    );

    /**
     * The comment the wiki writes in place of a template it leaves out once a parse outgrows the
     * post-expand include size.
     *
     * <p>
     * The parser writes it as markup, so it reaches the rendered text whole; a page source a
     * composite renders, or a page quoting the comment, carries it with the {@code <} escaped and
     * does not match.
     */
    public static final @NotNull String INCLUDE_SIZE_WARNING = "<!-- WARNING: template omitted, post-expand include size too large -->";

    /**
     * The title the page was parsed as - the page a {@link WikiRequest#parsePage(String, String...)}
     * names, or the page it redirects to, or the page the wiki parses loose wikitext as.
     */
    @SerializedPath("parse.title")
    private @NotNull String title = "";

    /**
     * The id of the page parsed as.
     */
    @SerializedPath("parse.pageid")
    private long pageId;

    /**
     * The rendered HTML, one {@code div.mw-parser-output}; empty unless the request asked for
     * {@code text}.
     */
    @SerializedPath("parse.text")
    private @NotNull String text = "";

    /**
     * Every page, module and template the parse transcluded, in any namespace; empty unless the
     * request asked for {@code templates}.
     */
    @SerializedPath("parse.templates")
    private @NotNull ConcurrentList<Template> templates = Concurrent.newList();

    /**
     * The headings of the rendered text, in document order; empty unless the request asked for
     * {@code sections}.
     */
    @SerializedPath("parse.sections")
    private @NotNull ConcurrentList<Section> sections = Concurrent.newList();

    /**
     * The redirect the wiki followed to the page it parsed, when a parse of a page named a
     * redirect; empty for any other parse.
     */
    @SerializedPath("parse.redirects")
    private @NotNull ConcurrentList<WikiRedirect> redirects = Concurrent.newList();

    /**
     * Reads the counts every {@link DplQuery} result in the rendered text wrote in its footer.
     *
     * <p>
     * A query that matched no page wrote {@code 0} of {@code 0}. A footer the wiki left without
     * both counts is not among them.
     *
     * @return one entry per filled footer, in document order; empty unless the request asked for
     *     {@code text}
     */
    public @NotNull ConcurrentList<TableRows> getTableRows() {
        return FOOTER_TAG.matcher(this.text)
            .results()
            .map(tag -> FILLED_FOOTER.matcher(tag.group()))
            .filter(Matcher::matches)
            .map(footer -> new TableRows(count(footer.group(1)), count(footer.group(2))))
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Finds the first {@link DplQuery} footer in the rendered text that the wiki left without both
     * counts.
     *
     * @return the footer's opening tag as written, empty when every footer is filled
     */
    private @NotNull Optional<String> unfilledFooter() {
        return FOOTER_TAG.matcher(this.text)
            .results()
            .map(MatchResult::group)
            .filter(tag -> !FILLED_FOOTER.matcher(tag).matches())
            .findFirst();
    }

    /**
     * Collects the transcluded documents the wiki does not hold, each of which rendered as a red
     * link.
     *
     * @return the missing documents, in the order the parse reported them
     */
    public @NotNull ConcurrentList<Template> getMissingTemplates() {
        return this.templates.stream()
            .filter(template -> !template.isExisting())
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Asks whether the wiki left part of the parse out because it outgrew the post-expand include
     * size.
     *
     * @return {@code true} when the rendered text carries the wiki's {@link #INCLUDE_SIZE_WARNING}
     */
    public boolean isOverIncludeSize() {
        return this.text.contains(INCLUDE_SIZE_WARNING);
    }

    /**
     * Asks that the parse holds everything it names.
     *
     * <p>
     * Three failures leave the answer a success. A page or module that does not exist renders as a
     * red link; the check reads {@link #getTemplates()}, so it covers a parse that asked for
     * {@code templates} and passes one that did not. It fails on any missing document, including one
     * a page reaches through {@code {{PAGENAME}}}: loose wikitext is parsed as a page of the wiki's
     * choosing, so a page that transcludes a subpage of its own name reaches that page's subpage and
     * not its own. A parse over the post-expand include size writes {@link #INCLUDE_SIZE_WARNING} in
     * place of every template it leaves out, and a {@link DplQuery} result stops at its count; the
     * check reads the rendered text for the warning and for each query's footer, so it covers a parse
     * that asked for {@code text} and passes one that did not.
     *
     * <p>
     * A footer the wiki left without both counts fails the check too, since the result it closes
     * could be cut without saying so. A result that wrote no footer at all is invisible here;
     * {@link #requireTableRows(int)} is the check for it.
     *
     * @return this parse
     * @throws WikiMissingPageException if the parse reached a document the wiki does not hold
     * @throws WikiIncludeSizeException if the wiki left part of the parse out
     * @throws WikiMissingFooterException if a {@link DplQuery} footer is not filled with both counts
     * @throws WikiLimitReachedException if a {@link DplQuery} result holds fewer pages than its query
     *     matches
     */
    public @NotNull WikiParse requireComplete() throws WikiMissingPageException, WikiIncludeSizeException, WikiMissingFooterException, WikiLimitReachedException {
        ConcurrentList<Template> missing = this.getMissingTemplates();

        if (!missing.isEmpty()) {
            throw new WikiMissingPageException(
                "The parse reaches %s, which the wiki does not hold",
                missing.stream().map(template -> "'" + template.getTitle() + "'").collect(Collectors.joining(", "))
            );
        }

        if (this.isOverIncludeSize()) {
            throw new WikiIncludeSizeException(
                "The parse outgrew the wiki's post-expand include size, and the wiki left out what did not fit"
            );
        }

        Optional<String> unfilled = this.unfilledFooter();

        if (unfilled.isPresent()) {
            throw new WikiMissingFooterException(
                "A DynamicPageList result wrote its footer without both counts, '%s', so whether it stops short of the pages its query matches cannot be read",
                unfilled.get()
            );
        }

        Optional<TableRows> cut = this.getTableRows().stream().filter(TableRows::isCut).findFirst();

        if (cut.isPresent()) {
            throw new WikiLimitReachedException(
                "A DynamicPageList result holds '%s' of the '%s' pages its query matches - raise its count, up to '%s', or narrow the query",
                cut.get().rows(),
                cut.get().total(),
                DplQuery.MAX_COUNT
            );
        }

        return this;
    }

    /**
     * Asks that every {@link DplQuery} the parse renders wrote its footer, filled, so that
     * {@link #requireComplete()} reads the counts of every result.
     *
     * <p>
     * Every query writes exactly one footer, a query matching no page included, so a rendered text
     * holding fewer filled footers than the parse renders queries holds a result whose counts nothing
     * reads - one cut at its count among them. A parse that did not ask for {@code text} holds no
     * footer, and fails whenever it renders a query.
     *
     * @param queries the number of queries the parse renders
     * @return this parse
     * @throws WikiMissingFooterException if the rendered text holds fewer filled footers than the
     *     parse renders queries
     */
    public @NotNull WikiParse requireTableRows(int queries) throws WikiMissingFooterException {
        int footers = this.getTableRows().size();

        if (footers < queries) {
            throw new WikiMissingFooterException(
                "The parse renders '%s' DynamicPageList queries and its text holds '%s' filled footers, so whether each result stops short of the pages its query matches cannot be read",
                queries,
                footers
            );
        }

        return this;
    }

    private static int count(@NotNull String digits) {
        return digits.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(digits);
    }

    /**
     * The counts one {@link DplQuery} result wrote in its footer.
     *
     * @param rows the pages the result holds
     * @param total the pages the query matches, whatever its count
     */
    public record TableRows(int rows, int total) {

        /**
         * Asks whether the result stopped short of the pages its query matches.
         *
         * @return {@code true} when the query matches more pages than the result holds
         */
        public boolean isCut() {
            return this.total > this.rows;
        }

    }

    /**
     * One document a parse transcluded.
     */
    @Getter
    public static final class Template {

        /**
         * The namespace number - {@code 0} for an article, {@code 10} for a template, {@code 828}
         * for a module.
         */
        @SerializedName("ns")
        private int namespace;

        /**
         * The title, with its namespace.
         */
        private @NotNull String title = "";

        /**
         * Whether the wiki holds the document.
         */
        @SerializedName("exists")
        private boolean existing;

    }

    /**
     * One heading of a parse's rendered text.
     */
    @Getter
    public static final class Section {

        /**
         * The heading level, {@code 2} for {@code == Heading ==}.
         */
        private int level;

        /**
         * The heading as rendered, markup included.
         */
        private @NotNull String line = "";

        /**
         * The outline number, such as {@code 2.1}.
         */
        private @NotNull String number = "";

        /**
         * The section index the edit API takes, prefixed {@code T-} for a heading a transcluded
         * document carries.
         */
        private @NotNull String index = "";

        /**
         * The page the heading is written on.
         */
        @SerializedName("fromtitle")
        private @NotNull String fromTitle = "";

        /**
         * The anchor the heading links as.
         */
        private @NotNull String anchor = "";

    }

}
