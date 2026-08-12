package api.simplified.skyblock.model;

import api.simplified.skyblock.date.SkyBlockDate.Length;
import api.simplified.skyblock.date.SkyBlockDate;
import com.google.gson.annotations.SerializedName;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Objects;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Event that = (Event) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getTrigger(), that.getTrigger())
            && Objects.equals(this.getSchedules(), that.getSchedules())
            && Objects.equals(this.getMayorIds(), that.getMayorIds())
            && Objects.equals(this.getSuppressedByMayorIds(), that.getSuppressedByMayorIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getTrigger(), this.getSchedules(), this.getMayorIds(), this.getSuppressedByMayorIds());
    }

    /**
     * One rule for when an {@link Event} runs, as an anchor plus a list of phases that repeats
     * forever from it.
     *
     * <p>
     * The phase list alternates strictly by index - an even index is a run and an odd index is the
     * idle gap after it - and the whole list is one period. An odd-length list takes an implicit
     * trailing gap of zero, so the number of runs in a period is {@code (size + 1) / 2}.
     *
     * <p>
     * The anchor is any representative of a run rather than the first one, so a query behind the
     * anchor folds forward instead of running out of schedule. A rule this grammar cannot state at
     * all leaves the anchor absent and puts the rule in {@link #note}, which is what makes a
     * schedule carrying only prose different from no schedule at all.
     */
    @Getter
    @GsonType
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

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Schedule that = (Schedule) o;

            return this.getLimit() == that.getLimit()
                && Objects.equals(this.getMayorId(), that.getMayorId())
                && Objects.equals(this.getAnchor(), that.getAnchor())
                && Objects.equals(this.getPhases(), that.getPhases())
                && Objects.equals(this.getRotation(), that.getRotation())
                && Objects.equals(this.getNote(), that.getNote());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getMayorId(), this.getAnchor(), this.getPhases(), this.getLimit(), this.getRotation(), this.getNote());
        }

    }

    /**
     * One instant, held as the calendar components that name it rather than as a millisecond count.
     *
     * <p>
     * Every component defaults to the earliest position it can take, so an anchor spells out only
     * the coordinates that matter and the rest read as the opening of the year.
     */
    @Getter
    @GsonType
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

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Moment that = (Moment) o;

            return this.getYear() == that.getYear()
                && this.getMonth() == that.getMonth()
                && this.getDay() == that.getDay()
                && this.getHour() == that.getHour()
                && this.getMinute() == that.getMinute()
                && Objects.equals(this.getClock(), that.getClock());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getYear(), this.getMonth(), this.getDay(), this.getHour(), this.getMinute(), this.getClock());
        }

    }

    /**
     * One span of a {@link Schedule}'s cycle, held as unmultiplied unit counts rather than as a
     * millisecond total.
     *
     * <p>
     * The counts stay counts because the clock decides what each is worth. Multiplying a phase out
     * at load time would read a real-world week on the SkyBlock clock and be wrong by a factor of
     * 72.
     */
    @Getter
    @GsonType
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

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Phase that = (Phase) o;

            return this.getYears() == that.getYears()
                && this.getMonths() == that.getMonths()
                && this.getDays() == that.getDays()
                && this.getHours() == that.getHours()
                && this.getMinutes() == that.getMinutes()
                && Objects.equals(this.getClock(), that.getClock());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getYears(), this.getMonths(), this.getDays(), this.getHours(), this.getMinutes(), this.getClock());
        }

    }

    /**
     * A single run of a {@link Schedule}, bounded by the two real-world instants it opens and
     * closes at.
     *
     * <p>
     * It is worked out from the schedule's anchor and phases at the moment it is asked for, so it
     * belongs to no column and no row.
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
     * Which calendar a piece of a {@link Schedule} is measured against - the accelerated in-game one
     * or the real-world one.
     *
     * <p>
     * An anchor is read on the clock it names and a phase's unit counts are multiplied out on it, so
     * a schedule that mixes the two is stating that mixture deliberately.
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
     * What sets an event running, and so how much of its timing a {@link Schedule} is able to state.
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
