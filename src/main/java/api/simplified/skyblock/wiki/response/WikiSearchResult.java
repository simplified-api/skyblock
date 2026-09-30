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
import dev.simplified.gson.annotation.SerializedPath;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * The wiki's answer to a search, {@code action=query&list=search}: each page found, with a snippet of
 * its source around the match.
 *
 * <p>
 * The wiki answers up to five hundred hits at a time. A longer answer comes in parts, the
 * {@link #getContinuation() continuation} of each naming the next; {@link SkyBlockWikiContract} reads
 * every part and answers the hits of all of them in order, each page once, so a merged answer carries
 * no continuation.
 *
 * <p>
 * The wiki serves no hit past the {@link WikiRequest#MAX_SEARCH_HITS}th and says nothing of the ones
 * it leaves out, so every part is checked through {@link #requireReachable()} against the
 * {@link #getTotalHits() count} of the pages the search matches, which it carries.
 */
@Getter
public class WikiSearchResult extends WikiResponse {

    /**
     * The pages found, best match first.
     */
    @SerializedPath("query.search")
    private @NotNull ConcurrentList<Hit> hits = Concurrent.newList();

    /**
     * The number of pages the search matches, as the wiki counted them for the latest part that
     * carries the count; empty when no part carries one.
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
     * Asks that the search matches no more pages than the wiki serves hits for, so a walk through its
     * parts reaches every one.
     *
     * <p>
     * The part that reaches {@link WikiRequest#MAX_SEARCH_HITS} carries no continuation, so a search
     * matching more ends its walk as though it had found every page. The check reads
     * {@link #getTotalHits()} and passes an answer that carries no count.
     *
     * @return this answer
     * @throws WikiLimitReachedException if the search matches more pages than
     *     {@link WikiRequest#MAX_SEARCH_HITS}
     */
    public @NotNull WikiSearchResult requireReachable() throws WikiLimitReachedException {
        requireReachable(this.totalHits);
        return this;
    }

    /**
     * Appends the hits of the next part of the answer to this one's and takes its continuation, and
     * its count of the pages the search matches where it carries one.
     *
     * <p>
     * A part is read at an offset into the results as they stand when it is asked for, so a page the
     * index moved across that offset can come back in the next part; a page an earlier part already
     * found keeps its first place and is not appended again.
     *
     * @param next the next part
     */
    public void absorb(@NotNull WikiSearchResult next) {
        LinkedHashMap<String, Hit> merged = new LinkedHashMap<>();
        this.hits.forEach(hit -> merged.putIfAbsent(hit.title, hit));
        next.hits.forEach(hit -> merged.putIfAbsent(hit.title, hit));
        this.hits = Concurrent.newList(merged.values());
        this.continuation = next.continuation;

        if (next.totalHits.isPresent())
            this.totalHits = next.totalHits;
    }

    /**
     * Asks that a search's count of the pages it matches is within the hits the wiki serves.
     *
     * @param totalHits the count an answer carries, empty when it carries none
     * @throws WikiLimitReachedException if the count exceeds {@link WikiRequest#MAX_SEARCH_HITS}
     */
    static void requireReachable(@NotNull Optional<Long> totalHits) throws WikiLimitReachedException {
        if (totalHits.isPresent() && totalHits.get() > WikiRequest.MAX_SEARCH_HITS) {
            throw new WikiLimitReachedException(
                "The search matches '%s' pages, more than the '%s' hits the wiki serves for one search - narrow the query",
                totalHits.get(),
                WikiRequest.MAX_SEARCH_HITS
            );
        }
    }

    /**
     * One page a search found.
     */
    @Getter
    public static final class Hit {

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
         * The page id.
         */
        @SerializedName("pageid")
        private long pageId;

        /**
         * The source around the match as HTML: the source's characters escaped, and each match
         * wrapped in {@code <span class="searchmatch">}.
         */
        private @NotNull String snippet = "";

    }

}
