package api.simplified.skyblock.wiki.response;

import api.simplified.skyblock.wiki.SkyBlockWikiContract;
import api.simplified.skyblock.wiki.exception.WikiLimitReachedException;
import api.simplified.skyblock.wiki.request.WikiRequest;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentLinkedMap;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.annotation.SerializedPath;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * The wiki's answer to a read of page sources, {@code action=query&prop=revisions} in format
 * version 2: each page with the source of its latest revision.
 *
 * <p>
 * MediaWiki answers the sources of up to fifty pages at a time. A longer answer comes in parts, the
 * {@link #getContinuation() continuation} of each naming the next, and a page can arrive in one part
 * without its source and in a later one with it. {@link SkyBlockWikiContract} reads every part and
 * answers them merged, one entry per page, so a merged answer carries no continuation.
 *
 * <p>
 * Every read but a prefix read carries {@code redirects=1}: a title naming a redirect is answered as
 * the page it leads to, under that page's title, and {@link #getRedirects()} says which title led
 * where. A prefix read leaves every redirect under its prefix out, so it names none.
 *
 * <p>
 * A read of the pages a search generates carries the {@link #getTotalHits() count} of the pages the
 * search matches, which {@link #requireReachable()} checks against the hits the wiki serves; every
 * other read carries none.
 */
@Getter
public class WikiQueryResult extends WikiResponse {

    /**
     * The pages, each once, in the order the wiki first named them.
     */
    @SerializedPath("query.pages")
    private @NotNull ConcurrentList<Page> pages = Concurrent.newList();

    /**
     * The redirects the wiki followed, each once, in the order it first named them.
     */
    @SerializedPath("query.redirects")
    private @NotNull ConcurrentList<WikiRedirect> redirects = Concurrent.newList();

    /**
     * The number of pages the generating search matches, as the wiki counted them for the latest
     * part that carries the count; empty for a read no search generates.
     */
    @SerializedPath("query.searchinfo.totalhits")
    private @NotNull Optional<Long> totalHits = Optional.empty();

    /**
     * The {@code continue} object as the wiki wrote it, whose values are strings and numbers.
     */
    @Getter(AccessLevel.NONE)
    @SerializedName("continue")
    private @NotNull JsonObject continuation = new JsonObject();

    /**
     * Reads the parameters that ask for the next part of the answer.
     *
     * @return each parameter with its value as sent, in the order the wiki wrote them, empty on the
     *     last part
     */
    public @NotNull ConcurrentLinkedMap<String, String> getContinuation() {
        return continuationOf(this.continuation);
    }

    /**
     * Asks that the search generating the read matches no more pages than the wiki serves hits for,
     * so a walk through its parts reaches every one.
     *
     * <p>
     * The part that reaches {@link WikiRequest#MAX_SEARCH_HITS} carries no continuation, so a search
     * matching more ends its walk as though it had read every page. The check reads
     * {@link #getTotalHits()} and passes a read that carries no count, as every read no search
     * generates does.
     *
     * @return this answer
     * @throws WikiLimitReachedException if the search matches more pages than
     *     {@link WikiRequest#MAX_SEARCH_HITS}
     */
    public @NotNull WikiQueryResult requireReachable() throws WikiLimitReachedException {
        WikiSearchResult.requireReachable(this.totalHits);
        return this;
    }

    /**
     * Merges the next part of the answer into this one.
     *
     * <p>
     * A page this part already names takes the next part's revisions when it arrived without any; a
     * page it does not name is appended, and so is a redirect from a title it does not name. The
     * continuation becomes the next part's, and so does the count of the pages a search matches
     * where the next part carries one.
     *
     * @param next the next part
     */
    public void absorb(@NotNull WikiQueryResult next) {
        LinkedHashMap<String, Page> merged = new LinkedHashMap<>();
        this.pages.forEach(page -> merged.put(page.key(), page));

        next.pages.forEach(page -> merged.merge(page.key(), page, (held, incoming) ->
            held.revisions.isEmpty() && !incoming.revisions.isEmpty() ? incoming : held
        ));

        LinkedHashMap<String, WikiRedirect> redirects = new LinkedHashMap<>();
        this.redirects.forEach(redirect -> redirects.putIfAbsent(redirect.getFrom(), redirect));
        next.redirects.forEach(redirect -> redirects.putIfAbsent(redirect.getFrom(), redirect));

        this.pages = Concurrent.newList(merged.values());
        this.redirects = Concurrent.newList(redirects.values());
        this.continuation = next.continuation;

        if (next.totalHits.isPresent())
            this.totalHits = next.totalHits;
    }

    /**
     * One page of the answer.
     */
    @Getter
    public static final class Page {

        /**
         * The page id, {@code 0} for a page the wiki does not hold.
         */
        @SerializedName("pageid")
        private long pageId;

        /**
         * The namespace number, {@code 0} for an article.
         */
        @SerializedName("ns")
        private int namespace;

        /**
         * The title, with its namespace and spaces rather than underscores.
         */
        private @NotNull String title = "";

        /**
         * Whether the wiki holds no page under the title.
         */
        private boolean missing;

        /**
         * Whether the title is one no page can have - one holding a character a title never
         * carries, such as {@code [} - which the wiki answers with neither an id nor a source and
         * without marking it {@link #isMissing() missing}.
         */
        private boolean invalid;

        /**
         * The latest revision, once the answer reaches its source; empty for a missing page and for
         * an invalid title.
         */
        private @NotNull ConcurrentList<Revision> revisions = Concurrent.newList();

        /**
         * Reads the source of the page's latest revision.
         *
         * @return the source of the main slot, empty for a missing page and for an invalid title
         */
        public @NotNull Optional<String> getContent() {
            return this.revisions.stream()
                .findFirst()
                .map(revision -> revision.getSlots().get("main"))
                .map(Slot::getContent);
        }

        private @NotNull String key() {
            return this.pageId == 0 ? this.namespace + ":" + this.title : String.valueOf(this.pageId);
        }

    }

    /**
     * One revision of a page.
     */
    @Getter
    public static final class Revision {

        /**
         * The revision's content by slot name; a page's source is in {@code main}.
         */
        private @NotNull ConcurrentMap<String, Slot> slots = Concurrent.newMap();

    }

    /**
     * One slot of a revision.
     */
    @Getter
    public static final class Slot {

        /**
         * The content model, {@code wikitext} for an article and {@code Scribunto} for a module.
         */
        @SerializedName("contentmodel")
        private @NotNull String contentModel = "";

        /**
         * The source.
         */
        private @NotNull String content = "";

    }

}
