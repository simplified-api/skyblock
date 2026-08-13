package api.simplified.skyblock;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.tuple.single.SingleStream;
import dev.simplified.persistence.CacheExpiry;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.Repository;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.util.time.Stopwatch;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/**
 * A view of one model's {@link Repository} that answers from rows already in memory.
 *
 * <p>
 * Every query on the delegate opens a scoped session and runs a criteria select, so resolving ids
 * against a table costs one round trip per lookup over rows that have not moved. This holds the rows
 * of a single {@link Repository#findAll()} and scans those instead, and because every finder
 * {@link dev.simplified.collection.query.Sortable} offers is written over {@link #stream()}, holding
 * that one method is what reaches all of them.
 *
 * <p>
 * The view is built for one call and holds nothing itself. The rows live in one {@link Hold} per model
 * class and the delegate travels as a component rather than as a field, so a read that finds a fresh
 * hold writes no shared state at all. A hold is replaced whole, never mutated, so one table's rows
 * always come from one generation of that table.
 *
 * <p>
 * A held row can be up to one cache duration stale. The refresh cycle moves a repository's stamp
 * before it deletes the rows its source has withdrawn, so a hold taken between those two steps carries
 * a withdrawn row until the stamp moves again. A caller that needs the uncached answer asks the
 * session for the repository rather than going through {@link SkyBlockData#getRepository(Class)}.
 *
 * @param delegate the repository the rows are taken from and every unheld member is answered by
 * @param hold the cell holding that model's rows
 * @param <T> the entity type
 */
record ReferenceIndex<T extends JpaModel>(@NotNull Repository<T> delegate, @NotNull Hold<T> hold) implements Repository<T> {

    private static final @NotNull ConcurrentMap<Class<?>, Hold<?>> HOLDS = Concurrent.newMap();

    /**
     * Answers the one cell holding a model's rows, creating it on the first ask.
     *
     * @param tClass the model class the rows belong to
     * @param <T> the entity type
     * @return the hold for that class, which lives for the process
     */
    @SuppressWarnings("unchecked")
    static <T extends JpaModel> @NotNull Hold<T> holdFor(@NotNull Class<T> tClass) {
        return (Hold<T>) HOLDS.computeIfAbsent(tClass, ignored -> new Hold<>());
    }

    /**
     * Drops every hold, so the next read of any model takes its rows again.
     */
    static void clear() {
        HOLDS.clear();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull SingleStream<T> stream() throws JpaException {
        return SingleStream.of(this.hold.rows(this.delegate));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConcurrentList<T> findAll() throws JpaException {
        return this.hold.rows(this.delegate);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Duration getCacheDuration() {
        return this.delegate.getCacheDuration();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull CacheExpiry getCacheExpiry() {
        return this.delegate.getCacheExpiry();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Stopwatch getInitialLoad() {
        return this.delegate.getInitialLoad();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Stopwatch getLastRefresh() {
        return this.delegate.getLastRefresh();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Class<T> getType() {
        return this.delegate.getType();
    }

    /**
     * The one cell a model's rows are held in, and the only mutable state here.
     */
    static final class Hold<T extends JpaModel> {

        private volatile Held<T> held;

        private @NotNull ConcurrentList<T> rows(@NotNull Repository<T> delegate) {
            Held<T> current = this.held;

            if (current != null && current.isFresh(delegate))
                return current.rows();

            // two threads missing at once take the same rows twice and the later write wins, which
            // costs one duplicated select and leaves no reader holding a half-built list
            Held<T> taken = new Held<>(delegate, delegate.findAll(), delegate.getLastRefresh().completedAt());
            this.held = taken;
            return taken.rows();
        }

    }

    /**
     * One generation of a model's rows, and what they were taken from.
     *
     * @param delegate the repository instance the rows came from
     * @param rows the rows as that repository answered them
     * @param stamp the completion instant of the refresh the rows are of
     * @param <T> the entity type
     */
    private record Held<T extends JpaModel>(@NotNull Repository<T> delegate, @NotNull ConcurrentList<T> rows, @NotNull Instant stamp) {

        private boolean isFresh(@NotNull Repository<T> delegate) {
            // a teardown and rebuild reconstructs every repository, so identity moving is what says
            // these rows belong to a session that is gone
            if (this.delegate != delegate)
                return false;

            if (!this.stamp.equals(delegate.getLastRefresh().completedAt()))
                return false;

            // the stamp is written by the scheduler thread with no edge to this one, so the age is
            // read as well rather than instead - and a duration of zero means the table never refreshes
            Duration duration = delegate.getCacheDuration();
            return duration.isZero() || Duration.between(this.stamp, Instant.now()).compareTo(duration) < 0;
        }

    }

}
