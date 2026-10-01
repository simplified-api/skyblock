package api.simplified.skyblock.wiki.request;

import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A query against the wiki's Bucket store, rendered as the call chain
 * {@code bucket('b').join(...).select(...).where(...).limit(n).run()} the Bucket API runs.
 *
 * <p>
 * A bucket is a table the wiki's templates and modules write rows into as pages render, so a query
 * reads a value from every page that stored one, in one request. A {@link #join} pairs each row with
 * the rows of a second bucket on the server; a joined query names its columns {@code bucket.column}.
 *
 * <p>
 * The parts render in a fixed order - the bucket, each join, the selection, each condition, the
 * limit - whatever order they are named in. Every string is written between single quotes, with a
 * quote or a backslash inside it escaped by a backslash.
 *
 * <p>
 * Every query renders its limit, {@link #DEFAULT_LIMIT} when none is named. Bucket answers at most
 * the limit and says nothing of the rows past it, so the contract reads an answer holding as many
 * rows as the limit as one that may be cut short, and raises it.
 */
public final class BucketQuery {

    /**
     * The most rows one query answers; Bucket answers a larger limit with this many rows and no
     * sign that it lowered the limit.
     */
    public static final int MAX_LIMIT = 5000;

    /**
     * The limit a query that names none renders, the limit Bucket runs a query without one at, so
     * rendering it changes no answer.
     */
    public static final int DEFAULT_LIMIT = 500;

    /**
     * A {@code .limit(n)} call in a query's call chain, once its strings are blanked.
     */
    private static final @NotNull Pattern LIMIT_CALL = Pattern.compile("\\.\\s*limit\\s*\\(\\s*(\\d+)\\s*\\)");

    private final @NotNull String bucket;
    private final @NotNull ConcurrentList<String> joins = Concurrent.newList();
    private final @NotNull ConcurrentList<String> columns = Concurrent.newList();
    private final @NotNull ConcurrentList<String> conditions = Concurrent.newList();

    /**
     * The most rows the query answers, {@link #DEFAULT_LIMIT} until one is named.
     */
    @Getter
    private int limit = DEFAULT_LIMIT;

    private BucketQuery(@NotNull String bucket) {
        this.bucket = bucket;
    }

    /**
     * Starts a query over one bucket.
     *
     * @param bucket the bucket name
     * @return the query
     */
    public static @NotNull BucketQuery from(@NotNull String bucket) {
        return new BucketQuery(bucket);
    }

    /**
     * Pairs each row with the rows of another bucket whose column holds the same value, rendered
     * {@code .join('other','left','right')}.
     *
     * @param bucket the bucket joined in
     * @param leftColumn the column of the rows already read, qualified by its bucket
     * @param rightColumn the column of the joined bucket, qualified by that bucket
     * @return this query
     */
    public @NotNull BucketQuery join(@NotNull String bucket, @NotNull String leftColumn, @NotNull String rightColumn) {
        this.joins.add(String.format(".join(%s,%s,%s)", quote(bucket), quote(leftColumn), quote(rightColumn)));
        return this;
    }

    /**
     * Names the columns each row carries, rendered {@code .select('a','b')}.
     *
     * @param columns the column names, qualified by their bucket in a joined query
     * @return this query
     */
    public @NotNull BucketQuery select(@NotNull String @NotNull ... columns) {
        this.columns.addAll(Arrays.asList(columns));
        return this;
    }

    /**
     * Keeps the rows whose column holds a value, rendered {@code .where('column','value')}.
     *
     * @param column the column name
     * @param value the value the column holds
     * @return this query
     */
    public @NotNull BucketQuery where(@NotNull String column, @NotNull String value) {
        this.conditions.add(String.format(".where(%s,%s)", quote(column), quote(value)));
        return this;
    }

    /**
     * Caps the number of rows the query answers, rendered {@code .limit(n)}.
     *
     * @param limit the most rows answered, at most {@link #MAX_LIMIT}
     * @return this query
     * @throws IllegalArgumentException if the limit is not positive or is over {@link #MAX_LIMIT},
     *     which Bucket would lower in silence
     */
    public @NotNull BucketQuery limit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT)
            throw new IllegalArgumentException(String.format("A query answers between '1' and '%s' rows, not '%s'", MAX_LIMIT, limit));

        this.limit = limit;
        return this;
    }

    /**
     * Renders the query as the call chain the Bucket API runs.
     *
     * @return the call chain, its limit last before {@code .run()}
     */
    public @NotNull String render() {
        StringBuilder query = new StringBuilder("bucket(").append(quote(this.bucket)).append(')');
        this.joins.forEach(query::append);

        if (!this.columns.isEmpty())
            query.append(this.columns.stream().map(BucketQuery::quote).collect(Collectors.joining(",", ".select(", ")")));

        this.conditions.forEach(query::append);
        return query.append(".limit(").append(this.limit).append(").run()").toString();
    }

    /**
     * Reads the limit Bucket runs a rendered query at: its last {@code .limit(n)} call, lowered to
     * {@link #MAX_LIMIT}, or {@link #DEFAULT_LIMIT} for a query that calls none or names no
     * positive limit.
     *
     * <p>
     * A string in the query is skipped, so a condition quoting {@code .limit(1)} names no limit.
     *
     * @param query the call chain, as the {@code query} parameter of a Bucket request carries it
     * @return the most rows the query answers
     */
    public static int limitOf(@NotNull String query) {
        Matcher matcher = LIMIT_CALL.matcher(blankStrings(query));
        long limit = 0;

        while (matcher.find())
            limit = parseLimit(matcher.group(1));

        return runLimit(limit);
    }

    /**
     * Reads a limit as Bucket runs a query at it: lowered to {@link #MAX_LIMIT}, and
     * {@link #DEFAULT_LIMIT} for one that is not positive.
     *
     * @param limit the limit a query names
     * @return the most rows the query answers
     */
    public static int runLimit(long limit) {
        return limit < 1 ? DEFAULT_LIMIT : (int) Math.min(limit, MAX_LIMIT);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String toString() {
        return this.render();
    }

    private static @NotNull String quote(@NotNull String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /**
     * Replaces every character inside a quoted string with a space, keeping the quotes, so nothing
     * a string holds reads as a call.
     *
     * @param query the call chain
     * @return the call chain with its strings blanked
     */
    private static @NotNull String blankStrings(@NotNull String query) {
        StringBuilder blanked = new StringBuilder(query.length());
        char open = 0;
        boolean escaped = false;

        for (char c : query.toCharArray()) {
            if (open == 0) {
                blanked.append(c);

                if (c == '\'' || c == '"')
                    open = c;
            } else if (escaped) {
                blanked.append(' ');
                escaped = false;
            } else if (c == '\\') {
                blanked.append(' ');
                escaped = true;
            } else if (c == open) {
                blanked.append(c);
                open = 0;
            } else
                blanked.append(' ');
        }

        return blanked.toString();
    }

    private static long parseLimit(@NotNull String digits) {
        return digits.length() > 9 ? Long.MAX_VALUE : Long.parseLong(digits);
    }

}
