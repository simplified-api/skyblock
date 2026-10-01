package api.simplified.skyblock.wiki.client;

import api.simplified.skyblock.wiki.SkyBlockWikiContract;
import dev.simplified.annotations.Getter;
import dev.simplified.client.exception.RateLimitException;
import dev.simplified.client.ratelimit.RateLimit;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Supplier;

/**
 * How a paged read waits out the client's rate limit before each of its parts.
 *
 * <p>
 * The client refuses a request over its route's rate limit with a {@link RateLimitException} before
 * sending it. A paged read of more parts than the {@code api.php} limit leaves in its window meets
 * that refusal part way, and one started once the window is spent meets it on its first part;
 * {@link SkyBlockWikiContract} sends every part of a paged read, the first among them, through
 * {@link #send(Supplier)}, which waits and sends the same part again until the limiter lets it
 * through, so the read completes rather than failing or losing the parts it has read. A request
 * that is not part of a paged read is not sent through here, and raises its refusal at once.
 *
 * <p>
 * Each wait is read off the refusal's {@link RateLimit}: one request's share of the policy's window -
 * a second for the {@code api.php} route's sixty requests a minute - or, for a policy that names a
 * reset instant, the time until it. The client's own refusal carries the policy its route declares,
 * which names no reset instant, so every wait the client's limiter causes is the share. The limiter
 * opens a new window no later than one window after the refusal, and a refused send costs no
 * request, so the part is tried again after each wait until it goes through.
 *
 * <p>
 * The waits for one part are bounded by {@link #getMaxWait()}, counted from its first refusal: a part
 * still refused once that much time has passed raises the refusal, so a limiter that never lets it
 * through fails the read rather than holding it. A {@code 429} the wiki answered is raised at once -
 * a request was sent, and the client's limiter is not what refused it - and so is a refusal whose
 * wait is interrupted, with the thread's interrupt status set again and the interruption suppressed
 * on the refusal.
 */
@Getter
public final class WikiPacing {

    /**
     * The longest one part waits by default, two windows of the {@code api.php} route: one always
     * suffices for the client's own limiter, and the second absorbs another caller on the same client
     * spending the new window first.
     */
    public static final @NotNull Duration DEFAULT_MAX_WAIT = Duration.ofMinutes(2);

    /**
     * The pacing every paged read of the contract uses unless handed another: the system clock, a
     * sleep of the calling thread, and {@link #DEFAULT_MAX_WAIT}.
     */
    public static final @NotNull WikiPacing DEFAULT = new WikiPacing(InstantSource.system(), Thread::sleep, DEFAULT_MAX_WAIT);

    /**
     * The clock the waits of one part are measured against.
     */
    private final @NotNull InstantSource clock;

    /**
     * How a wait is spent.
     */
    private final @NotNull Sleeper sleeper;

    /**
     * The longest one part waits, counted from its first refusal, before its refusal is raised.
     */
    private final @NotNull Duration maxWait;

    private WikiPacing(@NotNull InstantSource clock, @NotNull Sleeper sleeper, @NotNull Duration maxWait) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.maxWait = maxWait;
    }

    /**
     * Builds a pacing over a clock and a way to wait.
     *
     * @param clock the clock the waits are measured against
     * @param sleeper how a wait is spent
     * @param maxWait the longest one part waits, {@link Duration#ZERO} to raise the first refusal
     * @return the pacing
     * @throws IllegalArgumentException if the longest wait is negative
     */
    public static @NotNull WikiPacing of(@NotNull InstantSource clock, @NotNull Sleeper sleeper, @NotNull Duration maxWait) {
        if (maxWait.isNegative())
            throw new IllegalArgumentException(String.format("A part waits for no less than nothing, not '%s'", maxWait));

        return new WikiPacing(clock, sleeper, maxWait);
    }

    /**
     * Sends one part of a paged read, the first or any after it, waiting out each refusal of the
     * client's rate limit and sending the part again.
     *
     * @param part sends the part and answers it
     * @param <T> the answer type
     * @return the answer
     * @throws RateLimitException if the wiki answered the part with a {@code 429}, if the part is
     *     still refused once {@link #getMaxWait()} has passed since its first refusal, or if a wait
     *     is interrupted
     */
    public <T> @NotNull T send(@NotNull Supplier<T> part) throws RateLimitException {
        Instant firstRefusal = null;

        while (true) {
            try {
                return part.get();
            } catch (RateLimitException refusal) {
                if (refusal.isServerEnforced())
                    throw refusal;

                Instant now = this.clock.instant();

                if (firstRefusal == null)
                    firstRefusal = now;

                Duration left = this.maxWait.minus(Duration.between(firstRefusal, now));

                if (left.isNegative() || left.isZero())
                    throw refusal;

                Duration wait = waitAfter(refusal.getRateLimit(), now);

                try {
                    this.sleeper.sleep(wait.compareTo(left) < 0 ? wait : left);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    refusal.addSuppressed(interrupted);
                    throw refusal;
                }
            }
        }
    }

    /**
     * Reads how long to wait before sending a refused part again.
     *
     * @param policy the policy that refused the part
     * @param now the instant of the refusal
     * @return the time until the reset instant the policy names, or else one request's share of the
     *     policy's window, never less than a millisecond
     */
    static @NotNull Duration waitAfter(@NotNull RateLimit policy, @NotNull Instant now) {
        long reset = policy.getResetEpochMillis() - now.toEpochMilli();

        if (reset > 0)
            return Duration.ofMillis(reset);

        return Duration.ofMillis(Math.max(1L, policy.getWindowDurationMillis() / Math.max(1L, policy.getLimit())));
    }

    /**
     * Spends one wait.
     */
    @FunctionalInterface
    public interface Sleeper {

        /**
         * Waits for a duration.
         *
         * @param duration how long to wait
         * @throws InterruptedException if the wait is interrupted
         */
        void sleep(@NotNull Duration duration) throws InterruptedException;

    }

}
