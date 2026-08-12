package api.simplified.skyblock.date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the calendar arithmetic, where a season and a month are one coordinate under two spellings.
 */
class SkyBlockDateTest {

    private static final SkyBlockDate ANCHOR = new SkyBlockDate(278, Season.EARLY_SPRING, 1, 0);

    @Test
    @DisplayName("a season names its month, counting from one")
    void seasonNamesItsMonth() {
        assertThat(Season.EARLY_SPRING.getMonth(), is(equalTo(1)));
        assertThat(Season.LATE_SPRING.getMonth(), is(equalTo(3)));
        assertThat(Season.LATE_SUMMER.getMonth(), is(equalTo(6)));
        assertThat(Season.LATE_WINTER.getMonth(), is(equalTo(12)));

        // the month is carried rather than derived from declaration order, so reordering the enum
        // cannot silently move a date
        for (Season season : Season.values())
            assertThat(Season.of(season.getMonth()), is(equalTo(season)));
    }

    @Test
    @DisplayName("a month number outside the year names no season")
    void unknownMonthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Season.of(0));
        assertThrows(IllegalArgumentException.class, () -> Season.of(13));
    }

    @Test
    @DisplayName("adding a season and subtracting it again returns the same instant")
    void addAndSubtractAgreeOnEverySeason() {
        // the two directions read the season through one conversion, so every season round-trips;
        // a direction that shifted the month by a different amount would land elsewhere
        for (Season season : Season.values()) {
            SkyBlockDate roundTrip = ANCHOR.add(0, season, 1).subtract(0, season, 1);

            assertThat(roundTrip.getRealTime(), is(equalTo(ANCHOR.getRealTime())));
        }
    }

    @Test
    @DisplayName("adding a season moves by that season's month count")
    void addingASeasonMovesWholeMonths() {
        for (Season season : Season.values()) {
            SkyBlockDate bySeason = ANCHOR.add(0, season, 1);
            SkyBlockDate byMonth = ANCHOR.add(0, season.getMonth(), 1);

            assertThat(bySeason.getRealTime(), is(equalTo(byMonth.getRealTime())));
        }
    }

}
