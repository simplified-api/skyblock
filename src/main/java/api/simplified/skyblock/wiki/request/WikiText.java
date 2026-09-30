package api.simplified.skyblock.wiki.request;

import api.simplified.skyblock.wiki.exception.WikiRedirectException;
import api.simplified.skyblock.wiki.response.WikiParse;
import dev.simplified.annotations.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Composes the wikitext a composite parse renders, one part per document, and reads the one piece
 * of a page's source the contract checks, a redirect's stub - an article's, template's or
 * module's.
 *
 * <p>
 * The wiki renders a composite on its servers, so one request carries every document the parts name.
 * A part names its document by title and nothing else: a page that does not exist renders as a red
 * link and the answer is still a success, which is why a composite's answer is checked by
 * {@link WikiParse#requireComplete()} before anything reads it.
 */
@UtilityClass
public class WikiText {

    /**
     * The namespace prefix of a Lua module.
     */
    public static final @NotNull String MODULE_NAMESPACE = "Module:";

    /**
     * The namespace prefix of a template.
     */
    public static final @NotNull String TEMPLATE_NAMESPACE = "Template:";

    /**
     * The opening of a redirect's source as MediaWiki reads it: the magic word in any case, an
     * optional colon, and a link whose label, if any, is dropped.
     */
    private static final @NotNull Pattern REDIRECT = Pattern.compile("^\\s*#REDIRECT\\s*:?\\s*\\[\\[(.*?)(?:\\|.*?)?]]", Pattern.CASE_INSENSITIVE);

    /**
     * The whole source of a module stored as a redirect, the one Lua statement Scribunto writes
     * when a module is moved: {@code return require [[Module:Target]]}, the target in the module
     * namespace, nothing before it and nothing but whitespace after it.
     */
    private static final @NotNull Pattern MODULE_REDIRECT = Pattern.compile(
        "\\Areturn require \\[\\[(" + Pattern.quote(MODULE_NAMESPACE) + "[^\\[\\]|\\n]+)]]\\s*\\z"
    );

    /**
     * Composes the transclusion of a page, {@code {{:<title>}}}, which renders the page in place.
     *
     * <p>
     * The leading colon names the title as written, so a title without a namespace is an article
     * rather than a template. The title is used as given; spaces and underscores read the same.
     *
     * @param title the page title, with its namespace when it has one
     * @return the wikitext
     */
    public static @NotNull String transclusion(@NotNull String title) {
        return "{{:" + title + "}}";
    }

    /**
     * Composes the Lua source of a module as a preformatted block,
     * {@code {{#tag:pre|{{msgnw:Module:<module>}}}}}.
     *
     * <p>
     * {@code msgnw} writes a page's source with every character escaped, so the wiki shows it rather
     * than running it. Rendered as a paragraph, that text loses its line structure: the text of a
     * {@code <p>} collapses every run of whitespace. Wrapped in a {@code <pre>}, the text keeps every
     * line and tab, and reads back with each character reference decoded to the character it escapes.
     *
     * @param module the module name, without the {@code Module:} namespace
     * @return the wikitext
     */
    public static @NotNull String moduleSource(@NotNull String module) {
        return "{{#tag:pre|{{msgnw:" + MODULE_NAMESPACE + module + "}}}}";
    }

    /**
     * Composes the source of any page as a preformatted block, {@code {{#tag:pre|{{msgnw::<title>}}}}},
     * with the line structure {@link #moduleSource(String)} keeps.
     *
     * <p>
     * The colon before the title names it as written; without it, a title with no namespace resolves
     * in the template namespace.
     *
     * @param title the page title, with its namespace when it has one
     * @return the wikitext
     */
    public static @NotNull String pageSource(@NotNull String title) {
        return "{{#tag:pre|{{msgnw::" + title + "}}}}";
    }

    /**
     * Reads the page a redirect's source sends its reader to.
     *
     * <p>
     * A redirect's source is a stub, in one of two forms:
     * <ul>
     *     <li><b>Wikitext</b> - {@code #REDIRECT [[Chocolate Factory#Hoppity's Collection]]}: the
     *     magic word, in any case, opening the source, then a link to the target. MediaWiki takes
     *     whitespace before the magic word and around the link, and a colon between the two. Only
     *     the English magic word is read, which is the one this wiki's redirects are written with.
     *     A module holding wikitext redirects this way too.</li>
     *     <li><b>Lua</b> - {@code return require [[Module:Stat/Data]]}, the whole of a module's
     *     source: Scribunto stores a module's redirect as that one statement, spelled exactly so,
     *     with the target in the module namespace.</li>
     * </ul>
     *
     * @param source a page's source
     * @return the target as the stub writes it, a section after its {@code #} included, or empty
     *     when the source is not a redirect's
     */
    public static @NotNull Optional<String> redirectTarget(@NotNull String source) {
        Matcher module = MODULE_REDIRECT.matcher(source);

        if (module.matches())
            return Optional.of(module.group(1).trim());

        Matcher matcher = REDIRECT.matcher(source);

        if (!matcher.find())
            return Optional.empty();

        String target = matcher.group(1).trim();
        return target.isEmpty() ? Optional.empty() : Optional.of(target);
    }

    /**
     * Asks that a page's source is the page's own rather than a redirect's stub.
     *
     * <p>
     * A raw read answers a redirect with its stub and a status of 200, so the read succeeds with a
     * source that is not the page's. Both forms {@link #redirectTarget(String)} reads are refused.
     *
     * @param title the title the source was read under
     * @param source the source
     * @return the source
     * @throws WikiRedirectException if the source is a redirect's stub
     */
    public static @NotNull String requireSource(@NotNull String title, @NotNull String source) throws WikiRedirectException {
        Optional<String> target = redirectTarget(source);

        if (target.isPresent()) {
            throw new WikiRedirectException(
                "The page '%s' is a redirect to '%s'",
                WikiRequest.normalize(title),
                target.get()
            );
        }

        return source;
    }

    /**
     * Composes one part per name and concatenates them in the order given.
     *
     * @param composer the composer of one part
     * @param names the names, one per part
     * @return the wikitext
     * @throws IllegalArgumentException if no name is given
     */
    static @NotNull String join(@NotNull Function<String, String> composer, @NotNull String @NotNull ... names) {
        if (names.length == 0)
            throw new IllegalArgumentException("The composite names no document");

        return Arrays.stream(names).map(composer).collect(Collectors.joining());
    }

}
