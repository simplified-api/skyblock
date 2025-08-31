package dev.sbs.minecraftapi.skyblock.date;

import dev.sbs.api.builder.EqualsBuilder;
import dev.sbs.api.builder.HashCodeBuilder;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.SimpleDate;
import dev.sbs.minecraftapi.skyblock.election.Election;
import dev.sbs.minecraftapi.skyblock.election.SpecialElection;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.text.SimpleDateFormat;

/**
 * SkyBlock DateTime converter.
 */
public class SkyBlockDate extends SimpleDate {

    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("MMMMM dd, yyyy HH:mm z");
    public static final ConcurrentList<String> ZOO_CYCLE = Concurrent.newUnmodifiableList("ELEPHANT", "GIRAFFE", "BLUE_WHALE", "TIGER", "LION", "MONKEY");
    public static final ConcurrentList<String> SPECIAL_MAYOR_CYCLE = Concurrent.newUnmodifiableList("SCORPIUS", "DERPY", "JERRY");

    public SkyBlockDate(@NotNull Season season, @Range(from = 1, to = 31) int day) {
        this(season, day, 0);
    }

    public SkyBlockDate(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        this(season, day, hour, 0);
    }

    public SkyBlockDate(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        this(getRealTime(season, day, hour, minute), false);
    }

    public SkyBlockDate(int year, @NotNull Season season, @Range(from = 1, to = 31) int day) {
        this(year, (season.ordinal() + 1), day);
    }

    public SkyBlockDate(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day) {
        this(year, month, day, 0);
    }

    public SkyBlockDate(int year, @NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        this(year, season, day, hour, 0);
    }

    public SkyBlockDate(int year, @NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        this(year, (season.ordinal() + 1), day, hour, minute);
    }

    public SkyBlockDate(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        this(year, month, day, hour, 0);
    }

    public SkyBlockDate(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        this((Length.YEAR_MS * (year - 1)) + (Length.MONTH_MS * (month - 1)) + (Length.DAY_MS * (day - 1)) + (Length.HOUR_MS * hour) + (long) (Length.MINUTE_MS * minute), false);
    }

    public SkyBlockDate(long milliseconds) {
        this(milliseconds, true);
    }

    public SkyBlockDate(long milliseconds, boolean isRealTime) {
        super(isRealTime ? milliseconds : milliseconds + Launch.SKYBLOCK);
    }

    public @NotNull SkyBlockDate append(int year) {
        return new SkyBlockDate(this.getYear() + year, this.getMonth(), this.getDay(), this.getHour());
    }

    public @NotNull SkyBlockDate append(int year, @NotNull Season season) {
        return this.append(year, season.ordinal() + 1);
    }

    public @NotNull SkyBlockDate append(int year, @Range(from = 1, to = 12) int month) {
        return new SkyBlockDate(this.getYear() + year, this.getMonth() + month, this.getDay(), this.getHour());
    }

    public @NotNull SkyBlockDate append(int year, @NotNull Season season, @Range(from = 1, to = 31) int day) {
        return this.append(year, season.ordinal() + 1, day);
    }

    public @NotNull SkyBlockDate append(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day) {
        return this.append(year, month, day, 0);
    }

    public @NotNull SkyBlockDate append(int year, @NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return this.append(year, season.ordinal() + 1, day, hour);
    }

    public @NotNull SkyBlockDate append(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return new SkyBlockDate(this.getYear() + year, this.getMonth() + month, this.getDay() + day, this.getHour() + hour);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkyBlockDate that)) return false;

        return new EqualsBuilder()
            .append(this.getYear(), that.getYear())
            .append(this.getMonth(), that.getMonth())
            .append(this.getDay(), that.getDay())
            .append(this.getHour(), that.getHour())
            .build();
    }

    public static long getRealTime(@NotNull Season season) {
        return getRealTime(season, 1);
    }

    public static long getRealTime(@NotNull Season season, @Range(from = 1, to = 31) int day) {
        return getRealTime(season, day, 1);
    }

    public static long getRealTime(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return getRealTime(season, day, hour, 0);
    }

    public static long getRealTime(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        long month_millis = (season.ordinal() + 1) * Length.MONTH_MS;
        long day_millis = day * Length.DAY_MS;
        long hour_millis = hour * Length.HOUR_MS;
        long minute_millis = (long) (minute * Length.MINUTE_MS);

        return month_millis + day_millis + hour_millis - minute_millis;
    }

    public static @NotNull Election getNextMayor() {
        return getMayors(1).findFirstOrNull();
    }

    public static @NotNull ConcurrentList<Election> getMayors(int next) {
        return getMayors(next, new SkyBlockDate(System.currentTimeMillis()));
    }

    public static @NotNull ConcurrentList<Election> getMayors(int next, @NotNull SkyBlockDate fromDate) {
        next = Math.max(next, 1);
        SkyBlockDate specialMayorDate = new SkyBlockDate(Launch.MAYOR_ELECTIONS_START);
        ConcurrentList<Election> mayors = Concurrent.newList();

        while (mayors.size() < next) {
            if (specialMayorDate.getYear() >= fromDate.getYear())
                mayors.add(new Election(specialMayorDate.getYear()));

            specialMayorDate = specialMayorDate.append(8);
        }

        return mayors;
    }

    public static @NotNull SpecialElection getNextSpecialMayor() {
        return getSpecialMayors(1).findFirstOrNull();
    }

    public static @NotNull ConcurrentList<SpecialElection> getSpecialMayors(int next) {
        return getSpecialMayors(next, new SkyBlockDate(System.currentTimeMillis()));
    }

    public static @NotNull ConcurrentList<SpecialElection> getSpecialMayors(int next, @NotNull SkyBlockDate fromDate) {
        next = Math.max(next, 1);
        SkyBlockDate specialMayorDate = new SkyBlockDate(SkyBlockDate.Launch.SPECIAL_ELECTIONS_START);
        ConcurrentList<SpecialElection> specialMayors = Concurrent.newList();
        int iterations = 0;

        while (specialMayors.size() < next) {
            if (specialMayorDate.getYear() >= fromDate.getYear())
                specialMayors.add(new SpecialElection(specialMayorDate.getYear(), SPECIAL_MAYOR_CYCLE.get(iterations % 3)));

            specialMayorDate = specialMayorDate.append(8);
            iterations++;
        }

        return specialMayors;
    }

    public static long getSkyBlockTime(@NotNull Season season) {
        return getSkyBlockTime(season, 1);
    }

    public static long getSkyBlockTime(@NotNull Season season, @Range(from = 1, to = 31) int day) {
        return getSkyBlockTime(season, day, 1);
    }

    public static long getSkyBlockTime(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return getSkyBlockTime(season, day, hour, 0);
    }

    public static long getSkyBlockTime(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, int minute) {
        return getRealTime(season, day, hour, minute) - Launch.SKYBLOCK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDay() {
        long remainder = this.getSkyBlockTime() - ((this.getYear() - 1) * Length.YEAR_MS);
        remainder -= ((this.getMonth() - 1) * Length.MONTH_MS);
        return (int) (remainder / Length.DAY_MS) + 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHour() {
        long remainder = this.getSkyBlockTime() - ((this.getYear() - 1) * Length.YEAR_MS);
        remainder -= ((this.getMonth() - 1) * Length.MONTH_MS);
        remainder -= ((this.getDay() - 1) * Length.DAY_MS);
        return (int) (remainder / Length.HOUR_MS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinute() {
        long remainder = this.getSkyBlockTime() - ((this.getYear() - 1) * Length.YEAR_MS);
        remainder -= ((this.getMonth() - 1) * Length.MONTH_MS);
        remainder -= ((this.getDay() - 1) * Length.DAY_MS);
        remainder -= (this.getHour() * Length.HOUR_MS);
        return (int) (remainder / Length.MINUTE_MS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMonth() {
        long remainder = this.getSkyBlockTime() - ((this.getYear() - 1) * Length.YEAR_MS);
        return (int) (remainder / Length.MONTH_MS) + 1;
    }

    /**
     * Gets the current season of the year.
     *
     * @return season of the year
     */
    public @NotNull Season getSeason() {
        return Season.values()[this.getMonth() - 1];
    }

    @Override
    public int getSecond() {
        throw new UnsupportedOperationException("Minecraft has no concept of real seconds!");
    }

    /**
     * Get RealTime as SkyBlock time.
     *
     * @return skyblock time
     */
    public long getSkyBlockTime() {
        return this.getRealTime() - Launch.SKYBLOCK;
    }

    /**
     * Gets the number of years for the entire SkyBlock period.
     *
     * @return number of years
     */
    @Override
    public int getYear() {
        return (int) (this.getSkyBlockTime() / SkyBlockDate.Length.YEAR_MS) + 1;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(this.getYear())
            .append(this.getMonth())
            .append(this.getDay())
            .append(this.getHour())
            .build();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Launch {

        /**
         * The time SkyBlock launched in RealTime.
         */
        public static final long SKYBLOCK = 1560275700000L;

        /**
         * The time the Zoo launched in RealTime.
         */
        public static final long ZOO = SKYBLOCK + (Length.YEAR_MS * 66);

        /**
         * The time Jacob relaunched in RealTime.
         */
        public static final long JACOB = SKYBLOCK + (Length.YEAR_MS * 114);

        /**
         * The time Mayors launched in RealTime.
         */
        public static final long MAYOR_ELECTIONS_START = new SkyBlockDate(88, Season.LATE_SUMMER, 27, 0).getRealTime();

        /**
         * The time Mayors end in RealTime.
         */
        public static final long MAYOR_ELECTIONS_END = new SkyBlockDate(88, Season.LATE_SPRING, 27, 0).getRealTime();

        /**
         * The time Special Mayors launched in RealTime.
         */
        public static final long SPECIAL_ELECTIONS_START = new SkyBlockDate(96, Season.LATE_SUMMER, 27, 0).getRealTime();

        /**
         * The time Special Mayors end in RealTime.
         */
        public static final long SPECIAL_ELECTIONS_END = new SkyBlockDate(96, Season.LATE_SPRING, 27, 0).getRealTime();

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Length {

        public static final long MINUTES_TOTAL = 60;
        public static final long HOURS_TOTAL = 24;
        public static final long DAYS_TOTAL = 31;
        public static final long MONTHS_TOTAL = 12;

        public static final double MINUTE_MS = 50000.0 / 60;
        public static final long HOUR_MS = (long) (MINUTES_TOTAL * MINUTE_MS);
        public static final long DAY_MS = HOURS_TOTAL * HOUR_MS; // 1200000
        public static final long MONTH_MS = DAYS_TOTAL * DAY_MS; // 37200000
        public static final long YEAR_MS = MONTHS_TOTAL * MONTH_MS; // 446400000

        public static final long ZOO_CYCLE_MS = YEAR_MS / 2; // 223200000

    }

    public static class RealTime extends SkyBlockDate {

        public RealTime(long milliseconds) {
            super(milliseconds, true);
        }

    }

    public static class SkyBlockTime extends SkyBlockDate {

        public SkyBlockTime(long milliseconds) {
            super(milliseconds * 1000, false);
        }

    }

}
