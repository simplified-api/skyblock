package api.simplified.skyblock.wiki.response;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * One redirect the wiki followed to answer a request, which answers the page it leads to under that
 * page's own title.
 *
 * <p>
 * A parse of a page and every read of page sources but a prefix read carry {@code redirects=1}, so a
 * title naming a redirect is answered with its target; this is how the answer says which title led
 * where.
 */
@Getter
public final class WikiRedirect {

    /**
     * The title the request named, with spaces rather than underscores.
     */
    private @NotNull String from = "";

    /**
     * The title of the page the redirect leads to, which the answer carries in its place.
     */
    private @NotNull String to = "";

    /**
     * The section of the target the redirect names, empty when it names none.
     */
    @SerializedName("tofragment")
    private @NotNull String fragment = "";

}
