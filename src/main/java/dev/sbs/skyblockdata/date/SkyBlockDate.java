package dev.sbs.minecraftapi.skyblock.date;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.util.time.SimpleDate;
import dev.sbs.minecraftapi.client.hypixel.response.skyblock.election.Election;
import dev.sbs.minecraftapi.client.hypixel.response.skyblock.election.SpecialElection;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Objects;

/**
 * Converts between real-world timestamps and Hypixel SkyBlock's in-game calendar.
 *
 * <p>
 * SkyBlock uses an accelerated calendar where one real-world second equals roughly
 * 72 in-game seconds. The calendar has 12 {@link Season seasons} (months) of 31 days
 * each, yielding a 372-day year. All date components (year, month, day, hour, minute)
 * are derived from the elapsed milliseconds since the SkyBlock epoch
 * ({@link Launch#SKYBLOCK June 11, 2019}).
 *
 * <p>
 * Instances can be created from SkyBlock calendar components (year, season, day, hour,
 * minute) or from real-time epoch milliseconds. The {@link #getRealTime()} method
 * inherited from {@link SimpleDate} returns the underlying real-world epoch, while
 * {@link #getSkyBlockTime()} returns the elapsed SkyBlock milliseconds since launch.
 *
 * <p>
 * Static helpers provide conversions between {@link Season}-based coordinates and
 * millisecond offsets ({@link #getRealTime(Season, int, int, int)},
 * {@link #getSkyBlockTime(Season, int, int, int)}), as well as mayor election
 * forecasting ({@link #getNextMayor()}, {@link #getNextSpecialMayor()}).
 *
 * @see SimpleDate
 * @see Season
 * @see Launch
 * @see Length
 */
public class SkyBlockDate extends SimpleDate {

    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("MMMMM dd, yyyy HH:mm z");

    /**
     * The repeating cycle of Traveling Zoo pet types, in order.
     */
    public static final ConcurrentList<String> ZOO_CYCLE = Concurrent.newUnmodifiableList("ELEPHANT", "GIRAFFE", "BLUE_WHALE", "TIGER", "LION", "MONKEY");

    /**
     * The repeating cycle of special mayor names, in order.
     */
    public static final ConcurrentList<String> SPECIAL_MAYOR_CYCLE = Concurrent.newUnmodifiableList("SCORPIUS", "DERPY", "JERRY");

    /**
     * Creates a new {@code SkyBlockDate} for the given season and day in the current year,
     * with hour defaulting to 0.
     *
     * @param season the SkyBlock season (month)
     * @param day the day of the season (1-31)
     */
    public SkyBlockDate(@NotNull Season season, @Range(from = 1, to = 31) int day) {
        this(season, day, 0);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given season, day, and hour in the
     * current year, with minute defaulting to 0.
     *
     * @param season the SkyBlock season (month)
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     */
    public SkyBlockDate(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        this(season, day, hour, 0);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given season, day, hour, and minute
     * in the current year.
     *
     * @param season the SkyBlock season (month)
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     * @param minute the minute of the hour (0-59)
     */
    public SkyBlockDate(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        this(getRealTime(season, day, hour, minute), false);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given year, season, and day,
     * with hour defaulting to 0.
     *
     * @param year the SkyBlock year (1-based)
     * @param season the SkyBlock season (month)
     * @param day the day of the season (1-31)
     */
    public SkyBlockDate(int year, @NotNull Season season, @Range(from = 1, to = 31) int day) {
        this(year, (season.ordinal() + 1), day);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given year, month, and day,
     * with hour defaulting to 0.
     *
     * @param year the SkyBlock year (1-based)
     * @param month the month of the year (1-12)
     * @param day the day of the month (1-31)
     */
    public SkyBlockDate(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day) {
        this(year, month, day, 0);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given year, season, day, and hour,
     * with minute defaulting to 0.
     *
     * @param year the SkyBlock year (1-based)
     * @param season the SkyBlock season (month)
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     */
    public SkyBlockDate(int year, @NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        this(year, season, day, hour, 0);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given year, season, day, hour,
     * and minute.
     *
     * @param year the SkyBlock year (1-based)
     * @param season the SkyBlock season (month)
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     * @param minute the minute of the hour (0-59)
     */
    public SkyBlockDate(int year, @NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        this(year, (season.ordinal() + 1), day, hour, minute);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given year, month, day, and hour,
     * with minute defaulting to 0.
     *
     * @param year the SkyBlock year (1-based)
     * @param month the month of the year (1-12)
     * @param day the day of the month (1-31)
     * @param hour the hour of the day (0-23)
     */
    public SkyBlockDate(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        this(year, month, day, hour, 0);
    }

    /**
     * Creates a new {@code SkyBlockDate} for the given year, month, day, hour,
     * and minute.
     *
     * @param year the SkyBlock year (1-based)
     * @param month the month of the year (1-12)
     * @param day the day of the month (1-31)
     * @param hour the hour of the day (0-23)
     * @param minute the minute of the hour (0-59)
     */
    public SkyBlockDate(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        this((Length.YEAR_MS * (year - 1)) + (Length.MONTH_MS * (month - 1)) + (Length.DAY_MS * (day - 1)) + (Length.HOUR_MS * hour) + (long) (Length.MINUTE_MS * minute), false);
    }

    /**
     * Creates a new {@code SkyBlockDate} from a real-time epoch timestamp in milliseconds.
     *
     * @param milliseconds the real-time epoch timestamp in milliseconds
     */
    public SkyBlockDate(long milliseconds) {
        this(milliseconds, true);
    }

    /**
     * Creates a new {@code SkyBlockDate} from the given milliseconds, interpreted as
     * either a real-time epoch timestamp or elapsed SkyBlock milliseconds since launch.
     *
     * @param milliseconds the timestamp or elapsed time in milliseconds
     * @param isRealTime {@code true} to interpret as a real-time epoch timestamp,
     *                   {@code false} to interpret as SkyBlock elapsed milliseconds
     */
    public SkyBlockDate(long milliseconds, boolean isRealTime) {
        super(isRealTime ? milliseconds : milliseconds + Launch.SKYBLOCK);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given number of years added.
     *
     * @param year the number of years to add
     * @return a new date with the years added
     */
    public @NotNull SkyBlockDate add(int year) {
        return new SkyBlockDate(this.getYear() + year, this.getMonth(), this.getDay(), this.getHour());
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years and season offset added.
     *
     * @param year the number of years to add
     * @param season the season offset to add
     * @return a new date with the years and season added
     */
    public @NotNull SkyBlockDate add(int year, @NotNull Season season) {
        return this.add(year, season.ordinal() + 1);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years and months added.
     *
     * @param year the number of years to add
     * @param month the number of months to add (1-12)
     * @return a new date with the years and months added
     */
    public @NotNull SkyBlockDate add(int year, @Range(from = 1, to = 12) int month) {
        return new SkyBlockDate(this.getYear() + year, this.getMonth() + month, this.getDay(), this.getHour());
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, season offset, and
     * days added.
     *
     * @param year the number of years to add
     * @param season the season offset to add
     * @param day the number of days to add (1-31)
     * @return a new date with the years, season, and days added
     */
    public @NotNull SkyBlockDate add(int year, @NotNull Season season, @Range(from = 1, to = 31) int day) {
        return this.add(year, season.ordinal() + 1, day);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, months, and days added.
     *
     * @param year the number of years to add
     * @param month the number of months to add (1-12)
     * @param day the number of days to add (1-31)
     * @return a new date with the years, months, and days added
     */
    public @NotNull SkyBlockDate add(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day) {
        return this.add(year, month, day, 0);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, season offset, days,
     * and hours added.
     *
     * @param year the number of years to add
     * @param season the season offset to add
     * @param day the number of days to add (1-31)
     * @param hour the number of hours to add (0-23)
     * @return a new date with the years, season, days, and hours added
     */
    public @NotNull SkyBlockDate add(int year, @NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return this.add(year, season.ordinal() + 1, day, hour);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, months, days, and
     * hours added.
     *
     * @param year the number of years to add
     * @param month the number of months to add (1-12)
     * @param day the number of days to add (1-31)
     * @param hour the number of hours to add (0-23)
     * @return a new date with the years, months, days, and hours added
     */
    public @NotNull SkyBlockDate add(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return new SkyBlockDate(this.getYear() + year, this.getMonth() + month, this.getDay() + day, this.getHour() + hour);
    }

    /**
     * Compares this date to another object for equality based on SkyBlock calendar
     * components (year, month, day, hour).
     *
     * @param o the object to compare to
     * @return {@code true} if the other object is a {@code SkyBlockDate} with identical
     *         year, month, day, and hour
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkyBlockDate that)) return false;

        return this.getYear() == that.getYear()
            && this.getMonth() == that.getMonth()
            && this.getDay() == that.getDay()
            && this.getHour() == that.getHour();
    }

    /**
     * Returns the real-time millisecond offset for the first day of the given season,
     * with day defaulting to 1.
     *
     * @param season the SkyBlock season
     * @return the real-time millisecond offset
     */
    public static long getRealTime(@NotNull Season season) {
        return getRealTime(season, 1);
    }

    /**
     * Returns the real-time millisecond offset for the given season and day,
     * with hour defaulting to 1.
     *
     * @param season the SkyBlock season
     * @param day the day of the season (1-31)
     * @return the real-time millisecond offset
     */
    public static long getRealTime(@NotNull Season season, @Range(from = 1, to = 31) int day) {
        return getRealTime(season, day, 1);
    }

    /**
     * Returns the real-time millisecond offset for the given season, day, and hour,
     * with minute defaulting to 0.
     *
     * @param season the SkyBlock season
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     * @return the real-time millisecond offset
     */
    public static long getRealTime(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return getRealTime(season, day, hour, 0);
    }

    /**
     * Returns the real-time millisecond offset for the given season, day, hour,
     * and minute.
     *
     * @param season the SkyBlock season
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     * @param minute the minute of the hour (0-59)
     * @return the real-time millisecond offset
     */
    public static long getRealTime(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour, @Range(from = 0, to = 59) int minute) {
        long month_millis = (season.ordinal() + 1) * Length.MONTH_MS;
        long day_millis = day * Length.DAY_MS;
        long hour_millis = hour * Length.HOUR_MS;
        long minute_millis = (long) (minute * Length.MINUTE_MS);

        return month_millis + day_millis + hour_millis - minute_millis;
    }

    /**
     * Returns the next upcoming regular mayor {@link Election} from the current time.
     *
     * @return the next regular mayor election
     */
    public static @NotNull Election getNextMayor() {
        return getMayors(1).findFirstOrNull();
    }

    /**
     * Returns the next {@code next} upcoming regular mayor {@link Election Elections}
     * from the current time.
     *
     * @param next the number of upcoming elections to return (minimum 1)
     * @return a list of upcoming regular mayor elections
     */
    public static @NotNull ConcurrentList<Election> getMayors(int next) {
        return getMayors(next, new SkyBlockDate(System.currentTimeMillis()));
    }

    /**
     * Returns the next {@code next} upcoming regular mayor {@link Election Elections}
     * starting from the given date. Elections occur every SkyBlock year, beginning
     * at {@link Launch#MAYOR_ELECTIONS_START}.
     *
     * @param next the number of upcoming elections to return (minimum 1)
     * @param fromDate the SkyBlock date to start searching from
     * @return a list of upcoming regular mayor elections
     */
    public static @NotNull ConcurrentList<Election> getMayors(int next, @NotNull SkyBlockDate fromDate) {
        next = Math.max(next, 1);
        SkyBlockDate mayorDate = new SkyBlockDate(Launch.MAYOR_ELECTIONS_START);
        ConcurrentList<Election> mayors = Concurrent.newList();

        while (mayors.size() < next) {
            if (mayorDate.getYear() >= fromDate.getYear())
                mayors.add(new Election(mayorDate.getYear()));

            mayorDate = mayorDate.add(1);
        }

        return mayors;
    }

    /**
     * Returns the next upcoming {@link SpecialElection} from the current time.
     *
     * @return the next special mayor election
     */
    public static @NotNull SpecialElection getNextSpecialMayor() {
        return getSpecialMayors(1).findFirstOrNull();
    }

    /**
     * Returns the next {@code next} upcoming {@link SpecialElection SpecialElections}
     * from the current time.
     *
     * @param next the number of upcoming special elections to return (minimum 1)
     * @return a list of upcoming special mayor elections
     */
    public static @NotNull ConcurrentList<SpecialElection> getSpecialMayors(int next) {
        return getSpecialMayors(next, new SkyBlockDate(System.currentTimeMillis()));
    }

    /**
     * Returns the next {@code next} upcoming {@link SpecialElection SpecialElections}
     * starting from the given date. Special elections occur every 8 SkyBlock years,
     * beginning at {@link Launch#SPECIAL_ELECTIONS_START}, cycling through the
     * {@link #SPECIAL_MAYOR_CYCLE} in order.
     *
     * @param next the number of upcoming special elections to return (minimum 1)
     * @param fromDate the SkyBlock date to start searching from
     * @return a list of upcoming special mayor elections
     */
    public static @NotNull ConcurrentList<SpecialElection> getSpecialMayors(int next, @NotNull SkyBlockDate fromDate) {
        next = Math.max(next, 1);
        SkyBlockDate specialMayorDate = new SkyBlockDate(SkyBlockDate.Launch.SPECIAL_ELECTIONS_START);
        ConcurrentList<SpecialElection> specialMayors = Concurrent.newList();
        int iterations = 0;

        while (specialMayors.size() < next) {
            if (specialMayorDate.getYear() >= fromDate.getYear())
                specialMayors.add(new SpecialElection(specialMayorDate.getYear(), SPECIAL_MAYOR_CYCLE.get(iterations % 3)));

            specialMayorDate = specialMayorDate.add(8);
            iterations++;
        }

        return specialMayors;
    }

    /**
     * Returns the SkyBlock elapsed millisecond offset for the first day of the given
     * season, with day defaulting to 1.
     *
     * @param season the SkyBlock season
     * @return the SkyBlock elapsed millisecond offset
     */
    public static long getSkyBlockTime(@NotNull Season season) {
        return getSkyBlockTime(season, 1);
    }

    /**
     * Returns the SkyBlock elapsed millisecond offset for the given season and day,
     * with hour defaulting to 1.
     *
     * @param season the SkyBlock season
     * @param day the day of the season (1-31)
     * @return the SkyBlock elapsed millisecond offset
     */
    public static long getSkyBlockTime(@NotNull Season season, @Range(from = 1, to = 31) int day) {
        return getSkyBlockTime(season, day, 1);
    }

    /**
     * Returns the SkyBlock elapsed millisecond offset for the given season, day,
     * and hour, with minute defaulting to 0.
     *
     * @param season the SkyBlock season
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     * @return the SkyBlock elapsed millisecond offset
     */
    public static long getSkyBlockTime(@NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return getSkyBlockTime(season, day, hour, 0);
    }

    /**
     * Returns the SkyBlock elapsed millisecond offset for the given season, day, hour,
     * and minute.
     *
     * @param season the SkyBlock season
     * @param day the day of the season (1-31)
     * @param hour the hour of the day (0-23)
     * @param minute the minute of the hour
     * @return the SkyBlock elapsed millisecond offset
     */
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

    /**
     * Always throws {@link UnsupportedOperationException} because the SkyBlock calendar
     * has no concept of seconds.
     *
     * @return never returns normally
     * @throws UnsupportedOperationException always
     */
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

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(this.getYear(), this.getMonth(), this.getDay(), this.getHour());
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given number of years subtracted.
     *
     * @param year the number of years to subtract
     * @return a new date with the years subtracted
     */
    public @NotNull SkyBlockDate subtract(int year) {
        return new SkyBlockDate(this.getYear() - year, this.getMonth(), this.getDay(), this.getHour());
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years and season offset subtracted.
     *
     * @param year the number of years to subtract
     * @param season the season offset to subtract
     * @return a new date with the years and season subtracted
     */
    public @NotNull SkyBlockDate subtract(int year, @NotNull Season season) {
        return this.subtract(year, season.ordinal() + 1);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years and months subtracted.
     *
     * @param year the number of years to subtract
     * @param month the number of months to subtract (1-12)
     * @return a new date with the years and months subtracted
     */
    public @NotNull SkyBlockDate subtract(int year, @Range(from = 1, to = 12) int month) {
        return new SkyBlockDate(this.getYear() - year, this.getMonth() - month, this.getDay(), this.getHour());
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, season offset, and
     * days subtracted.
     *
     * @param year the number of years to subtract
     * @param season the season offset to subtract
     * @param day the number of days to subtract (1-31)
     * @return a new date with the years, season, and days subtracted
     */
    public @NotNull SkyBlockDate subtract(int year, @NotNull Season season, @Range(from = 1, to = 31) int day) {
        return this.subtract(year, season.ordinal() - 1, day);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, months, and days subtracted.
     *
     * @param year the number of years to subtract
     * @param month the number of months to subtract (1-12)
     * @param day the number of days to subtract (1-31)
     * @return a new date with the years, months, and days subtracted
     */
    public @NotNull SkyBlockDate subtract(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day) {
        return this.subtract(year, month, day, 0);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, season offset, days,
     * and hours subtracted.
     *
     * @param year the number of years to subtract
     * @param season the season offset to subtract
     * @param day the number of days to subtract (1-31)
     * @param hour the number of hours to subtract (0-23)
     * @return a new date with the years, season, days, and hours subtracted
     */
    public @NotNull SkyBlockDate subtract(int year, @NotNull Season season, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return this.subtract(year, season.ordinal() - 1, day, hour);
    }

    /**
     * Returns a new {@code SkyBlockDate} with the given years, months, days, and
     * hours subtracted.
     *
     * @param year the number of years to subtract
     * @param month the number of months to subtract (1-12)
     * @param day the number of days to subtract (1-31)
     * @param hour the number of hours to subtract (0-23)
     * @return a new date with the years, months, days, and hours subtracted
     */
    public @NotNull SkyBlockDate subtract(int year, @Range(from = 1, to = 12) int month, @Range(from = 1, to = 31) int day, @Range(from = 0, to = 23) int hour) {
        return new SkyBlockDate(this.getYear() - year, this.getMonth() - month, this.getDay() - day, this.getHour() - hour);
    }

    /**
     * Real-time epoch timestamps (in milliseconds) for key SkyBlock launch events
     * and election cycles.
     */
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

    /**
     * SkyBlock calendar unit counts and their real-time millisecond equivalents.
     *
     * <p>
     * One SkyBlock minute is {@code 50000 / 60} real milliseconds (roughly 833ms).
     * All larger units are integer multiples: 60 minutes per hour, 24 hours per day,
     * 31 days per month, 12 months per year.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Length {

        /** The number of minutes in one SkyBlock hour. */
        public static final long MINUTES_TOTAL = 60;

        /** The number of hours in one SkyBlock day. */
        public static final long HOURS_TOTAL = 24;

        /** The number of days in one SkyBlock month (season). */
        public static final long DAYS_TOTAL = 31;

        /** The number of months (seasons) in one SkyBlock year. */
        public static final long MONTHS_TOTAL = 12;

        /** Real-time milliseconds per SkyBlock minute (~833.33ms). */
        public static final double MINUTE_MS = 50000.0 / 60;

        /** Real-time milliseconds per SkyBlock hour (50,000ms). */
        public static final long HOUR_MS = (long) (MINUTES_TOTAL * MINUTE_MS);

        /** Real-time milliseconds per SkyBlock day (1,200,000ms). */
        public static final long DAY_MS = HOURS_TOTAL * HOUR_MS;

        /** Real-time milliseconds per SkyBlock month (37,200,000ms). */
        public static final long MONTH_MS = DAYS_TOTAL * DAY_MS;

        /** Real-time milliseconds per SkyBlock year (446,400,000ms). */
        public static final long YEAR_MS = MONTHS_TOTAL * MONTH_MS;

        /** Real-time milliseconds per Traveling Zoo cycle (half a SkyBlock year). */
        public static final long ZOO_CYCLE_MS = YEAR_MS / 2;

    }

    /**
     * A {@link SkyBlockDate} constructed from a real-time epoch timestamp in milliseconds.
     * Used as a Gson-serializable marker type for fields that store real-time values.
     */
    public static class RealTime extends SkyBlockDate {

        /**
         * Creates a new {@code RealTime} from the given real-time epoch timestamp.
         *
         * @param milliseconds the real-time epoch timestamp in milliseconds
         */
        public RealTime(long milliseconds) {
            super(milliseconds, true);
        }

        /**
         * Gson {@link TypeAdapter} that serializes {@link RealTime} as a raw epoch
         * millisecond value and deserializes a long back into a {@code RealTime}.
         */
        public static class Adapter extends TypeAdapter<SkyBlockDate.RealTime> {

            @Override
            public void write(@NotNull JsonWriter out, @NotNull SkyBlockDate.RealTime value) throws IOException {
                out.value(value.getRealTime());
            }

            @Override
            public SkyBlockDate.RealTime read(@NotNull JsonReader in) throws IOException {
                return new SkyBlockDate.RealTime(in.nextLong());
            }

        }

    }

    /**
     * A {@link SkyBlockDate} constructed from SkyBlock elapsed time in seconds.
     * Used as a Gson-serializable marker type for fields that store SkyBlock timestamps.
     */
    public static class SkyBlockTime extends SkyBlockDate {

        /**
         * Creates a new {@code SkyBlockTime} from the given SkyBlock elapsed time
         * in seconds (converted internally to milliseconds).
         *
         * @param milliseconds the SkyBlock elapsed time in seconds
         */
        public SkyBlockTime(long milliseconds) {
            super(milliseconds * 1000, false);
        }

        /**
         * Gson {@link TypeAdapter} that serializes {@link SkyBlockTime} as SkyBlock
         * elapsed seconds and deserializes a long back into a {@code SkyBlockTime}.
         */
        public static class Adapter extends TypeAdapter<SkyBlockDate.SkyBlockTime> {

            @Override
            public void write(@NotNull JsonWriter out, @NotNull SkyBlockDate.SkyBlockTime value) throws IOException {
                out.value((value.getRealTime() - Launch.SKYBLOCK) / 1000);
            }

            @Override
            public SkyBlockDate.SkyBlockTime read(@NotNull JsonReader in) throws IOException {
                return new SkyBlockDate.SkyBlockTime(in.nextLong());
            }

        }

    }

}
