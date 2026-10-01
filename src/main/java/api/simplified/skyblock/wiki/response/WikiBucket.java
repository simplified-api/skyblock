package api.simplified.skyblock.wiki.response;

import api.simplified.skyblock.wiki.exception.WikiLimitReachedException;
import api.simplified.skyblock.wiki.request.BucketQuery;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

/**
 * The wiki's answer to a Bucket query, {@code action=bucket}: the query as the wiki ran it and one
 * row per stored entry.
 *
 * <p>
 * A row is a JSON object keyed by the selected columns, {@code bucket.column} in a joined query. A
 * column the entry stored nothing in is absent from its row, and a repeated column holds an array.
 *
 * <p>
 * Bucket answers at most a query's limit and nothing past it, so an answer holding as many rows as
 * the limit may be cut there; {@link #requireUnderLimit(int)} is the check.
 */
@Getter
public class WikiBucket extends WikiResponse {

    /**
     * The query as the wiki ran it.
     */
    private @NotNull String bucketQuery = "";

    /**
     * The rows the query answered, at most its limit.
     */
    @SerializedName("bucket")
    private @NotNull ConcurrentList<JsonObject> rows = Concurrent.newList();

    /**
     * Asks that the answer holds fewer rows than its query's limit, so no row the query matches is
     * left out.
     *
     * <p>
     * An answer of exactly the limit is refused too: Bucket says nothing of the rows past a limit, so
     * a query matching exactly that many rows cannot be told from one matching more. The limit is
     * read as Bucket runs a query at it, so one over {@link BucketQuery#MAX_LIMIT} is checked at that
     * and one that is not positive at {@link BucketQuery#DEFAULT_LIMIT}.
     *
     * @param limit the limit the query names, {@link BucketQuery#getLimit()} for a query built here
     * @return this answer
     * @throws WikiLimitReachedException if the answer holds as many rows as the limit
     */
    public @NotNull WikiBucket requireUnderLimit(int limit) throws WikiLimitReachedException {
        int runs = BucketQuery.runLimit(limit);

        if (this.rows.size() >= runs) {
            throw new WikiLimitReachedException(
                runs < BucketQuery.MAX_LIMIT
                    ? "The Bucket query answered '%s' rows, as many as its limit, and may match more - raise the limit, up to '%s'"
                    : "The Bucket query answered '%s' rows, as many as its limit, and may match more - narrow the query, as '%s' is the most one query answers",
                this.rows.size(),
                BucketQuery.MAX_LIMIT
            );
        }

        return this;
    }

}
