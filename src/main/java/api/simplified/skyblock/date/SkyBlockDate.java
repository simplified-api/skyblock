package api.simplified.skyblock.date;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.simplified.util.time.SimpleDate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Objects;

/**
 * A real-world instant read as a position on Hypixel SkyBlock's in-game calendar.
 *
 * <p>
 * SkyBlock runs an accelerated clock: an hour is 50 real seconds, a day is 20 real minutes,
 * and a year is 12 {@link Season seasons} of 31 days - 372 days, a little over five real
 * days, so one real second covers 72 SkyBlock seconds. Every component (year, month, day,
 * hour, minute) is derived from the milliseconds elapsed since the game launched
 * ({@link Launch#SKYBLOCK}); nothing is stored per component.
 *
 * <p>
 * An instance can be built from calendar components or from a real-time epoch value.
 * {@link #getRealTime()}, inherited from {@link SimpleDate}, reports the underlying
 * real-world epoch; {@link #getSkyBlockTime()} reports the SkyBlock milliseconds since
 * launch. A calendar month is named by a {@link Season}, which carries its own month
 * number, so a season and a month are the same coordinate under two spellings.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Calendar">Calendar</a>
 */
public class SkyBlockDate extends SimpleDate {

    /**
     * Pattern that renders a real-world instant as a full date with its time zone.
     */
    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("MMMMM dd, yyyy HH:mm z");

    /**
     * Creates a new {@code SkyBlockDate} for the given year, season, and day,
     * with hour defaulting to 0.
     *
     * @param year the SkyBlock year (1-based)
     * @param season the SkyBlock season (month)
     * @param day the day of the season (1-31)
     */
    public SkyBlockDate(int year, @NotNull Season season, @Range(from = 1, to = 31) int day) {
        this(year, season.getMonth(), day);
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
        this(year, season.getMonth(), day, hour, minute);
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
        return this.add(year, season.getMonth());
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
        return this.add(year, season.getMonth(), day);
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
        return this.add(year, season.getMonth(), day, hour);
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
     * Day of the SkyBlock month this date falls on, counted from 1.
     */
    @Override
    public int getDay() {
        long remainder = this.getSkyBlockTime() - ((this.getYear() - 1) * Length.YEAR_MS);
        remainder -= ((this.getMonth() - 1) * Length.MONTH_MS);
        return (int) (remainder / Length.DAY_MS) + 1;
    }

    /**
     * Hour of the SkyBlock day this date falls in, counted from 0.
     */
    @Override
    public int getHour() {
        long remainder = this.getSkyBlockTime() - ((this.getYear() - 1) * Length.YEAR_MS);
        remainder -= ((this.getMonth() - 1) * Length.MONTH_MS);
        remainder -= ((this.getDay() - 1) * Length.DAY_MS);
        return (int) (remainder / Length.HOUR_MS);
    }

    /**
     * Minute of the SkyBlock hour this date falls in, counted from 0.
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
     * Month of the SkyBlock year this date falls in, counted from 1 - the ordinal of its
     * {@link Season} plus one.
     */
    @Override
    public int getMonth() {
        long remainder = this.getSkyBlockTime() - ((this.getYear() - 1) * Length.YEAR_MS);
        return (int) (remainder / Length.MONTH_MS) + 1;
    }

    /**
     * {@link Season} naming the month this date falls in.
     */
    public @NotNull Season getSeason() {
        return Season.values()[this.getMonth() - 1];
    }

    /**
     * Unsupported - the SkyBlock calendar has no unit below the minute.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public int getSecond() {
        throw new UnsupportedOperationException("Minecraft has no concept of real seconds!");
    }

    /**
     * Milliseconds elapsed on the SkyBlock clock since the game launched, the value every
     * calendar component is derived from.
     */
    public long getSkyBlockTime() {
        return this.getRealTime() - Launch.SKYBLOCK;
    }

    /**
     * SkyBlock year this date falls in, counted from 1 at launch.
     */
    @Override
    public int getYear() {
        return (int) (this.getSkyBlockTime() / SkyBlockDate.Length.YEAR_MS) + 1;
    }

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
        return this.subtract(year, season.getMonth());
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
        return this.subtract(year, season.getMonth(), day);
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
        return this.subtract(year, season.getMonth(), day, hour);
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
     * Real-world epoch milliseconds for the moments the SkyBlock calendar is anchored to.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Launch {

        /**
         * The instant Hypixel SkyBlock launched, the origin every SkyBlock date is measured
         * from.
         */
        public static final long SKYBLOCK = 1560275700000L;

    }

    /**
     * SkyBlock calendar unit counts and what each one costs in real-world milliseconds.
     *
     * <p>
     * One SkyBlock minute is {@code 50000 / 60} real milliseconds, roughly 833ms. Every
     * larger unit is an integer multiple of it - 60 minutes per hour, 24 hours per day, 31
     * days per month, 12 months per year.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Length {

        /**
         * The number of minutes in one SkyBlock hour.
         */
        public static final long MINUTES_TOTAL = 60;

        /**
         * The number of hours in one SkyBlock day.
         */
        public static final long HOURS_TOTAL = 24;

        /**
         * The number of days in one SkyBlock month (season).
         */
        public static final long DAYS_TOTAL = 31;

        /**
         * The number of months (seasons) in one SkyBlock year.
         */
        public static final long MONTHS_TOTAL = 12;

        /**
         * Real-time milliseconds per SkyBlock minute (~833.33ms).
         */
        public static final double MINUTE_MS = 50000.0 / 60;

        /**
         * Real-time milliseconds per SkyBlock hour (50,000ms).
         */
        public static final long HOUR_MS = (long) (MINUTES_TOTAL * MINUTE_MS);

        /**
         * Real-time milliseconds per SkyBlock day (1,200,000ms).
         */
        public static final long DAY_MS = HOURS_TOTAL * HOUR_MS;

        /**
         * Real-time milliseconds per SkyBlock month (37,200,000ms).
         */
        public static final long MONTH_MS = DAYS_TOTAL * DAY_MS;

        /**
         * Real-time milliseconds per SkyBlock year (446,400,000ms).
         */
        public static final long YEAR_MS = MONTHS_TOTAL * MONTH_MS;

    }

    /**
     * A {@link SkyBlockDate} whose wire form is a real-world epoch millisecond value.
     *
     * <p>
     * Declaring a field as this type rather than the parent is what selects
     * {@link Adapter}, so the same calendar type binds either wire scale.
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
         * Gson {@link TypeAdapter} binding a {@link RealTime} to and from a bare epoch
         * millisecond number.
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
     * A {@link SkyBlockDate} whose wire form is the seconds elapsed on the SkyBlock clock
     * since launch.
     *
     * <p>
     * Declaring a field as this type rather than the parent is what selects
     * {@link Adapter}, so the same calendar type binds either wire scale.
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
         * Gson {@link TypeAdapter} binding a {@link SkyBlockTime} to and from a count of
         * SkyBlock seconds elapsed since launch.
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
