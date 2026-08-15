package api.simplified.skyblock.model;

import api.simplified.skyblock.date.SkyBlockDate.Length;
import api.simplified.skyblock.date.SkyBlockDate;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.ForeignIds;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A timed happening in SkyBlock - the Mayor Election, the Traveling Zoo's visit, Jerry's Workshop
 * opening for winter.
 *
 * <p>
 * An event is a name, a {@link Trigger} and the {@link Schedule schedules} that say when it runs.
 * A schedule is an anchor and a repeating list of phases rather than a list of dates, so one row
 * answers for every past and future occurrence, and {@link #getOccurrenceAt(long)} and
 * {@link #getNextOccurrence(long)} walk it. An event whose timing no anchor can state carries a
 * schedule holding only a note, which is why a schedule of nothing but prose still says more than
 * no schedule at all.
 *
 * <p>
 * A mayor-driven event also names the {@link Mayor Mayors} that bring it and the ones that hold it
 * back, and each list of ids sits beside the rows it resolves to. Who holds office is not itself on
 * the calendar, so the walk answers the calendar question alone - a schedule naming a mayor reports
 * the positions that mayor's perk fills, and a caller wanting the runs that really happened
 * intersects those with the term that was actually won.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Events">Events</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "events")
public class Event implements JpaModel {

    /**
     * The event's id, spelled as the event's name - {@code TRAVELING_ZOO}.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the event.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * A line saying what happens while the event runs.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * What sets the event running, and so how much of its timing a schedule is able to state. The
     * column is spelled {@code trigger_type} because {@code TRIGGER} is reserved in H2.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private @NotNull Trigger trigger = Trigger.UNSCHEDULED;

    /**
     * When the event runs, as anchored phase cycles. The column is Gson-serialised, which is also
     * what keeps a {@link Moment}'s {@code year}, {@code month}, {@code day} and {@code hour} out of
     * H2, where all four are reserved.
     */
    @Column(name = "schedules", nullable = false)
    private @NotNull ConcurrentList<Schedule> schedules = Concurrent.newUnmodifiableList();

    /**
     * Ids of the {@link Mayor Mayors} whose terms bring the event, bound from the wire key
     * {@code mayors}.
     */
    @SerializedName("mayors")
    @Column(name = "mayor_ids", nullable = false)
    private @NotNull ConcurrentList<String> mayorIds = Concurrent.newUnmodifiableList();

    /**
     * Ids of the {@link Mayor Mayors} whose terms hold the event back, bound from the wire key
     * {@code suppressedBy}.
     */
    @SerializedName("suppressedBy")
    @Column(name = "suppressed_by_mayor_ids", nullable = false)
    private @NotNull ConcurrentList<String> suppressedByMayorIds = Concurrent.newUnmodifiableList();

    /**
     * The {@link Mayor} rows behind {@link #mayorIds}, filled in by the repository layer rather than
     * stored in a column.
     */
    @ForeignIds("mayorIds")
    private transient @NotNull ConcurrentList<Mayor> mayors = Concurrent.newList();

    /**
     * The {@link Mayor} rows behind {@link #suppressedByMayorIds}, filled in by the repository layer
     * rather than stored in a column.
     */
    @ForeignIds("suppressedByMayorIds")
    private transient @NotNull ConcurrentList<Mayor> suppressedByMayors = Concurrent.newList();

    /**
     * Derives the event's slot in a whole-year rotation, as the anchor year's remainder over the
     * number of SkyBlock years its schedule repeats on.
     *
     * <p>
     * A twelve-year zodiac event therefore answers {@code 0..11}, and every SkyBlock year leaving
     * that same remainder is a year the event runs. It is a remainder rather than a year number, so
     * comparing it against a real SkyBlock year is the mistake to avoid.
     *
     * @return the anchor year's remainder, empty unless the event has exactly one computable
     *     schedule anchored on the SkyBlock calendar whose period is a whole multiple of
     *     {@link Length#YEAR_MS} larger than one year
     */
    public @NotNull Optional<Integer> getSlot() {
        ConcurrentList<Schedule> computable = this.getSchedules()
            .stream()
            .filter(Schedule::isComputable)
            .collect(Concurrent.toUnmodifiableList());

        if (computable.size() != 1) return Optional.empty();

        Schedule schedule = computable.getFirst();
        long period = schedule.getPeriod();

        // a real-world anchor counts Gregorian years, which are no part of this remainder
        if (schedule.getAnchor().orElseThrow().getClock() != Clock.SKYBLOCK) return Optional.empty();
        if (period <= Length.YEAR_MS || Math.floorMod(period, Length.YEAR_MS) != 0) return Optional.empty();

        int periodYears = (int) Math.floorDiv(period, Length.YEAR_MS);
        return schedule.getAnchor().map(anchor -> Math.floorMod(anchor.getYear(), periodYears));
    }

    /**
     * Answers whether the event is under way at a real-world instant.
     *
     * <p>
     * A schedule whose run phase is zero-length models an instant rather than a span, so an event
     * of that shape - the Dark Auction is one - is never running and is only ever reachable through
     * {@link #getNextOccurrence(long)}.
     *
     * @param realTime the real-world epoch milliseconds to test
     * @return {@code true} when some schedule has an occurrence covering that instant
     */
    public boolean isRunningAt(long realTime) {
        return this.getOccurrenceAt(realTime).isPresent();
    }

    /**
     * Finds the occurrence covering a real-world instant, taking schedules in declaration order.
     *
     * @param realTime the real-world epoch milliseconds to cover
     * @return the occurrence that instant falls inside, empty when none does
     */
    public @NotNull Optional<Occurrence> getOccurrenceAt(long realTime) {
        for (Schedule schedule : this.getSchedules()) {
            Optional<Occurrence> occurrence = schedule.occurrenceAt(realTime);

            if (occurrence.isPresent())
                return occurrence;
        }

        return Optional.empty();
    }

    /**
     * Finds the earliest-starting occurrence at or after a real-world instant, across every
     * schedule.
     *
     * @param realTime the real-world epoch milliseconds to measure from
     * @return the soonest occurrence still to come, empty when no schedule has one
     */
    public @NotNull Optional<Occurrence> getNextOccurrence(long realTime) {
        return this.getSchedules()
            .stream()
            .map(schedule -> schedule.nextOccurrence(realTime))
            .flatMap(Optional::stream)
            .min(Comparator.comparingLong(Occurrence::getStart));
    }

    /**
     * One rule for when an {@link Event} runs, held as a starting point plus a repeating on/off
     * pattern rather than a list of dates, so a single row answers for every occurrence there has
     * ever been and every one still to come.
     *
     * <p>
     * The Traveling Zoo, in full:
     *
     * <pre><code>
     * {
     *   "anchor": { "year": 50, "month": 4 },
     *   "phases": [
     *     { "days": 3 },
     *     { "months": 5, "days": 28 },
     *     { "days": 3 },
     *     { "months": 5, "days": 28 }
     *   ],
     *   "rotation": ["BLUE_WHALE", "TIGER", "LION", "MONKEY", "ELEPHANT", "GIRAFFE"]
     * }
     * </code></pre>
     *
     * <p>
     * Laid out along the calendar, that reads:
     *
     * <pre><code>
     *   Y50 M4                    Y50 M10                    Y51 M4
     *   +------+---------------------+------+---------------------+
     *   |  0   |          1          |  2   |          3          |
     *   | RUN  |         gap         | RUN  |         gap         |
     *   |3 days|       5mo 28d       |3 days|       5mo 28d       |
     *   +------+---------------------+------+---------------------+
     *   '----------------------- one period ----------------------'
     *
     *   372 days is exactly one SkyBlock year, so the zoo lands on
     *   Early Summer and Early Winter of every year, forever.
     * </code></pre>
     *
     * <p>
     * The phase list alternates strictly by index - an even index is a run and an odd index is the
     * idle gap after it - and the whole list is one period. An odd-length list takes an implicit
     * trailing gap of zero, so the number of runs in a period is {@code (size + 1) / 2}. Nothing
     * requires the gaps to match each other, which is what lets four gatherings a month and a
     * single yearly visit share one grammar.
     *
     * <p>
     * The anchor is any representative of a run rather than the first one, so a query behind the
     * anchor folds forward instead of running out of schedule - a twelve-year event anchored on
     * year 479 still answers for year 407, at run index {@code -6}. A rule this grammar cannot
     * state at all leaves the anchor absent and puts the rule in {@link #note}, which is what makes
     * a schedule carrying only prose different from no schedule at all.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Schedule {

        /**
         * Id of the {@link Mayor} whose term gates this rule, bound from the wire key {@code mayor}
         * and absent for a schedule the calendar alone drives.
         */
        @SerializedName("mayor")
        private @NotNull Optional<String> mayorId = Optional.empty();

        /**
         * The instant the phase list is measured from, absent when no fixed point exists to walk
         * from.
         */
        private @NotNull Optional<Moment> anchor = Optional.empty();

        /**
         * The alternating run and gap spans making up one period, starting with a run.
         */
        private @NotNull ConcurrentList<Phase> phases = Concurrent.newUnmodifiableList();

        /**
         * How many runs the schedule ever produces, {@code 0} for a schedule that repeats without
         * end.
         */
        private int limit = 0;

        /**
         * What each run of the cycle offers, walked in order and wrapping - the Traveling Zoo's pets
         * are one.
         */
        private @NotNull ConcurrentList<String> rotation = Concurrent.newUnmodifiableList();

        /**
         * A recurrence rule stated in prose because the anchor and phase grammar cannot hold it.
         */
        private @NotNull String note = "";

        /**
         * Answers whether the schedule has a fixed point to walk its phases from.
         *
         * @return {@code true} when an anchor is present
         */
        public boolean isComputable() {
            return this.getAnchor().isPresent();
        }

        /**
         * Sums every phase into the length of one full cycle.
         *
         * @return the period in real-world milliseconds, {@code 0} when no phase is declared
         */
        public long getPeriod() {
            return this.getPhases()
                .stream()
                .mapToLong(Phase::toMilliseconds)
                .sum();
        }

        /**
         * Measures the first phase, which is the span a run of this schedule lasts.
         *
         * @return the run length in real-world milliseconds, {@code 0} when no phase is declared
         */
        public long getRunLength() {
            return this.getPhases().isEmpty() ? 0 : this.getPhases().getFirst().toMilliseconds();
        }

        /**
         * Selects the rotation entry a run lands on, wrapping so that the list repeats for as long
         * as the schedule does.
         *
         * @param index the run's index, counted from the anchor and negative behind it
         * @return the entry that run offers, empty when the schedule declares no rotation
         */
        public @NotNull Optional<String> rotationAt(long index) {
            if (this.getRotation().isEmpty()) return Optional.empty();
            return Optional.of(this.getRotation().get((int) Math.floorMod(index, this.getRotation().size())));
        }

        /**
         * Walks the cycle to the phase a real-world instant falls in, and reports it when that
         * phase is a run.
         *
         * <p>
         * A missing or empty phase list is one run at the anchor rather than none. A zero-length
         * run phase covers no instant at all, so the walk falls through it into the gap that
         * follows and an instantaneous event is reachable only through
         * {@link #nextOccurrence(long)}.
         *
         * @param realTime the real-world epoch milliseconds to cover
         * @return the occurrence that instant falls inside, empty when it lands in a gap, past the
         *     schedule's limit, or the schedule has no anchor
         */
        public @NotNull Optional<Occurrence> occurrenceAt(long realTime) {
            if (this.getAnchor().isEmpty()) return Optional.empty();

            long anchorMs = this.getAnchor().get().toRealTime();
            long period = this.getPeriod();

            // a missing or empty cycle is ONE run at the anchor, not none
            if (this.getPhases().isEmpty() || period <= 0) {
                long end = anchorMs + this.getRunLength();
                return realTime >= anchorMs && realTime < end
                    ? Optional.of(new Occurrence(this, anchorMs, end, 0))
                    : Optional.empty();
            }

            long runs = (this.getPhases().size() + 1) / 2;
            long cycle = Math.floorDiv(realTime - anchorMs, period);
            long offset = Math.floorMod(realTime - anchorMs, period);
            long elapsed = 0;

            for (int index = 0; index < this.getPhases().size(); index++) {
                long length = this.getPhases().get(index).toMilliseconds();

                if (offset < elapsed + length) {
                    if (index % 2 != 0) return Optional.empty();

                    long run = (cycle * runs) + (index / 2);

                    if (this.getLimit() > 0 && (run < 0 || run >= this.getLimit()))
                        return Optional.empty();

                    long start = anchorMs + (cycle * period) + elapsed;
                    return Optional.of(new Occurrence(this, start, start + length, run));
                }

                elapsed += length;
            }

            return Optional.empty();
        }

        /**
         * Walks the cycle forward to the first run starting at or after a real-world instant.
         *
         * <p>
         * The scan covers the cycle that instant sits in and the one after it, which is enough for
         * any phase list, and a bounded schedule cannot start behind its anchor because it has no
         * run there.
         *
         * @param realTime the real-world epoch milliseconds to measure from
         * @return the soonest occurrence still to come, empty when the schedule has no anchor or
         *     has already spent its limit
         */
        public @NotNull Optional<Occurrence> nextOccurrence(long realTime) {
            if (this.getAnchor().isEmpty()) return Optional.empty();

            long anchorMs = this.getAnchor().get().toRealTime();
            long period = this.getPeriod();

            if (this.getPhases().isEmpty() || period <= 0) {
                return realTime <= anchorMs
                    ? Optional.of(new Occurrence(this, anchorMs, anchorMs + this.getRunLength(), 0))
                    : Optional.empty();
            }

            long runs = (this.getPhases().size() + 1) / 2;
            long first = Math.floorDiv(realTime - anchorMs, period);

            // a bounded schedule has no run before its anchor, so the scan cannot start behind it
            if (this.getLimit() > 0) first = Math.max(first, 0);

            for (long cycle = first; cycle <= first + 1; cycle++) {
                long elapsed = 0;

                for (int index = 0; index < this.getPhases().size(); index++) {
                    long length = this.getPhases().get(index).toMilliseconds();

                    if (index % 2 == 0) {
                        long start = anchorMs + (cycle * period) + elapsed;

                        if (start >= realTime) {
                            long run = (cycle * runs) + (index / 2);

                            if (this.getLimit() > 0 && run >= this.getLimit())
                                return Optional.empty();

                            return Optional.of(new Occurrence(this, start, start + length, run));
                        }
                    }

                    elapsed += length;
                }
            }

            return Optional.empty();
        }

    }

    /**
     * One point on a calendar, held as the components that name it rather than as a millisecond
     * count, so an anchor reads as a date a person can check rather than a number they cannot.
     *
     * <p>
     * Two anchors, one on each {@link Clock}:
     *
     * <pre><code>
     * { "year": 50, "month": 4 }
     * { "year": 2026, "month": 3, "day": 24, "hour": 5, "clock": "REAL" }
     * </code></pre>
     *
     * <p>
     * Every component defaults to the earliest position it can take, so the first of those names
     * Y50 M4 D1 at midnight and an anchor spells out only the coordinates that matter:
     *
     * <pre><code>
     *   component   default     reads as
     *   ---------   -------     --------
     *   year        1           year one
     *   month       1           the first month of that year
     *   day         1           the first day of that month
     *   hour        0           midnight
     *   minute      0           the top of the hour
     *   clock       SKYBLOCK    the accelerated in-game calendar
     * </code></pre>
     *
     * <p>
     * The component names are singular where a {@link Phase}'s are plural, and that is the whole
     * difference between the two - this names a position and a phase measures a length.
     * {@link #toRealTime()} resolves the components on the clock they name, and is the only place
     * the calendar arithmetic happens.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Moment {

        /**
         * Year the instant falls in, counted from one.
         */
        private int year = 1;

        /**
         * Month of that year, counted from one.
         */
        private int month = 1;

        /**
         * Day of that month, counted from one.
         */
        private int day = 1;

        /**
         * Hour of that day, counted from zero.
         */
        private int hour = 0;

        /**
         * Minute of that hour, counted from zero.
         */
        private int minute = 0;

        /**
         * Calendar the components are read on.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Clock clock = Clock.SKYBLOCK;

        /**
         * Resolves the components into the real-world instant they name, on this moment's clock.
         *
         * @return the real-world epoch milliseconds this moment sits at
         */
        public long toRealTime() {
            if (this.getClock() == Clock.SKYBLOCK)
                return new SkyBlockDate(this.getYear(), this.getMonth(), this.getDay(), this.getHour(), this.getMinute()).getRealTime();

            return LocalDateTime.of(this.getYear(), this.getMonth(), this.getDay(), this.getHour(), this.getMinute())
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        }

    }

    /**
     * One span of a {@link Schedule}'s cycle, held as unmultiplied unit counts because only the
     * {@link Clock} knows what a unit is worth.
     *
     * <p>
     * Three spans, two clocks:
     *
     * <pre><code>
     * { "days": 3 }                    three SkyBlock days, which is one real hour
     * { "days": 3, "clock": "REAL" }   three real days
     * { "months": 5, "days": 28 }      183 SkyBlock days
     * </code></pre>
     *
     * <p>
     * The counts stay counts because {@link #toMilliseconds()} is where the multiplication happens,
     * and the SkyBlock calendar runs at seventy-two times real time:
     *
     * <pre><code>
     *   SkyBlock unit       real-world span
     *   -------------       ---------------
     *   minute              833.33 ms
     *   hour, 60 minutes    50 seconds
     *   day, 24 hours       20 minutes
     *   month, 31 days      10 hours 20 minutes
     *   year, 12 months     5 days 4 hours
     * </code></pre>
     *
     * <p>
     * Multiplying a phase out at load time would read a real-world week on the SkyBlock clock and
     * be wrong by that same factor of seventy-two. Under {@link Clock#REAL} the year and month
     * counts contribute nothing, because neither a real-world year nor a real-world month is a
     * fixed length of time.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Phase {

        /**
         * Whole years the span covers.
         */
        private int years = 0;

        /**
         * Whole months the span covers on top of its years.
         */
        private int months = 0;

        /**
         * Whole days the span covers on top of its months.
         */
        private int days = 0;

        /**
         * Whole hours the span covers on top of its days.
         */
        private int hours = 0;

        /**
         * Whole minutes the span covers on top of its hours.
         */
        private int minutes = 0;

        /**
         * Calendar the unit counts are measured on.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Clock clock = Clock.SKYBLOCK;

        /**
         * Multiplies the unit counts out into a real-world span on this phase's clock.
         *
         * <p>
         * Under {@link Clock#REAL} the year and month counts contribute nothing, because neither a
         * real-world year nor a real-world month is a fixed length of time.
         *
         * @return the span in real-world milliseconds
         */
        public long toMilliseconds() {
            if (this.getClock() == Clock.REAL) {
                return TimeUnit.DAYS.toMillis(this.getDays())
                    + TimeUnit.HOURS.toMillis(this.getHours())
                    + TimeUnit.MINUTES.toMillis(this.getMinutes());
            }

            return (Length.YEAR_MS * this.getYears())
                + (Length.MONTH_MS * this.getMonths())
                + (Length.DAY_MS * this.getDays())
                + (Length.HOUR_MS * this.getHours())
                + (long) (Length.MINUTE_MS * this.getMinutes());
        }

    }

    /**
     * A single run of a {@link Schedule}, bounded by the two real-world instants it opens and
     * closes at, and worked out at the moment it is asked for so that it belongs to no column and
     * no row.
     *
     * <p>
     * The Traveling Zoo, asked about Y505 M4 D1, answers:
     *
     * <pre><code>
     *   schedule   the rule anchored at Y50 M4
     *   start      Y505 M4 D1
     *   end        three SkyBlock days later
     *   index      910
     *   rotation   ELEPHANT
     * </code></pre>
     *
     * <p>
     * The index counts runs from the anchor and goes negative behind it, so it is a position in the
     * cycle rather than a tally of runs that have happened:
     *
     * <pre><code>
     *   index:  -2        -1         0         1         2
     *           +-----+   +-----+   +-----+   +-----+   +-----+
     *      .....|#####|...|#####|...|#####|...|#####|...|#####|.....
     *           +-----+   +-----+   +-----+   +-----+   +-----+
     *                                  ^
     *                                  the run the anchor names
     * </code></pre>
     *
     * <p>
     * Index 910 is 455 SkyBlock years past that anchor at two runs a year, and
     * {@link #getRotation()} takes it modulo the six-entry rotation to reach the pet that visit
     * offers. An occurrence whose {@link #end} equals its {@link #start} models an instant rather
     * than a span.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public static class Occurrence {

        /**
         * The schedule this run came out of.
         */
        private final @NotNull Schedule schedule;

        /**
         * Real-world epoch milliseconds the run opens at.
         */
        private final long start;

        /**
         * Real-world epoch milliseconds the run closes at, equal to {@link #start} for a run that
         * models an instant rather than a span.
         */
        private final long end;

        /**
         * The run's index, counted from the anchor and negative for a run behind it.
         */
        private final long index;

        /**
         * Selects the entry of the schedule's rotation this run offers.
         *
         * @return the rotation entry for this index, empty when the schedule declares no rotation
         */
        public @NotNull Optional<String> getRotation() {
            return this.schedule.rotationAt(this.index);
        }

    }

    /**
     * Which calendar a piece of a {@link Schedule} is measured against, named on the piece itself so
     * that one schedule can pin a real-world date and still measure its spans in SkyBlock days.
     *
     * <p>
     * The SkyBlock Anniversary mixes them deliberately - a SkyBlock anchor, a real-world span:
     *
     * <pre><code>
     * {
     *   "anchor": { "year": 495, "month": 11, "day": 21 },
     *   "phases": [ { "days": 7, "clock": "REAL" } ],
     *   "limit": 1
     * }
     * </code></pre>
     *
     * <p>
     * What the choice decides:
     *
     * <pre><code>
     *   clock      a Moment reads as       a Phase multiplies out by
     *   -----      -----------------       -------------------------
     *   SKYBLOCK   a SkyBlock date         fixed spans, minute up to year
     *   REAL       a UTC date and time     days, hours and minutes only
     * </code></pre>
     *
     * <p>
     * An anchor is read on the clock it names and a phase's unit counts are multiplied out on it, so
     * a schedule that mixes the two is stating that mixture deliberately. A real-world year and a
     * real-world month vary in length, which is why neither measures anything a phase can carry.
     */
    public enum Clock {

        /**
         * The SkyBlock calendar, on which every unit from the minute up to the year is a fixed span.
         */
        SKYBLOCK,

        /**
         * The real-world calendar, on which a year and a month vary in length and so measure nothing
         * a phase can carry.
         */
        REAL

    }

    /**
     * What sets an event running, and so how much of its timing a {@link Schedule} is able to state,
     * which makes it the field to read before trusting a walk to answer anything.
     *
     * <p>
     * It sets the expectation the schedules then meet:
     *
     * <pre><code>
     * { "trigger": "SKYBLOCK_CALENDAR", "schedules": [ { "anchor": ..., "phases": [ ... ] } ] }
     * { "trigger": "UNSCHEDULED",       "schedules": [] }
     * </code></pre>
     *
     * <p>
     * Read across all five:
     *
     * <pre><code>
     *   trigger             a schedule for it states
     *   -------             ------------------------
     *   SKYBLOCK_CALENDAR   an anchor and phases, in full
     *   MAYOR_PERK          the calendar slots the perk fills, gated on a term
     *   REAL_WORLD          a fixed window as anchor and phases, a moving one as prose
     *   PLAYER_STARTED      nothing, and the schedule list is empty
     *   UNSCHEDULED         nothing, and the schedule list is empty
     * </code></pre>
     *
     * <p>
     * A calendar-driven event follows entirely from an anchor and a phase list. A mayoral term, a
     * moving real-world window and a one-off announcement do not, so a schedule for one of those
     * falls back on a note describing the rule the grammar cannot express.
     */
    public enum Trigger {

        /**
         * An event fixed to the SkyBlock calendar, whose every past and future occurrence follows
         * from an anchor and a phase list.
         */
        SKYBLOCK_CALENDAR,

        /**
         * An event a mayor brings with them, running inside the term that elected them.
         */
        MAYOR_PERK,

        /**
         * An event placed on the real-world calendar rather than the SkyBlock one.
         */
        REAL_WORLD,

        /**
         * An event a player opens themselves, so it keeps no schedule of its own.
         */
        PLAYER_STARTED,

        /**
         * An event announced whenever Hypixel chooses, with no recurrence to walk.
         */
        UNSCHEDULED

    }

}
