package api.simplified.skyblock.model;

import api.simplified.skyblock.date.Election;
import api.simplified.skyblock.date.Season;
import api.simplified.skyblock.date.SkyBlockDate.Length;
import api.simplified.skyblock.date.SkyBlockDate;
import com.google.gson.Gson;
import dev.simplified.gson.GsonSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Covers the schedule walk, where an anchor and a repeating phase list stand in for a list of dates.
 */
class EventTest {

    private static final String MAYOR_ELECTION = """
        {
          "id": "MAYOR_ELECTION",
          "name": "Mayor Election",
          "trigger": "SKYBLOCK_CALENDAR",
          "schedules": [
            {
              "anchor": { "year": 88, "month": 6, "day": 27 },
              "phases": [
                { "months": 9 },
                { "months": 3 }
              ]
            }
          ]
        }
        """;

    private static final String TRAVELING_ZOO = """
        {
          "id": "TRAVELING_ZOO",
          "name": "Traveling Zoo",
          "trigger": "SKYBLOCK_CALENDAR",
          "schedules": [
            {
              "anchor": { "year": 50, "month": 4 },
              "phases": [
                { "days": 3 },
                { "months": 5, "days": 28 },
                { "days": 3 },
                { "months": 5, "days": 28 }
              ],
              "rotation": ["BLUE_WHALE", "TIGER", "LION", "MONKEY", "ELEPHANT", "GIRAFFE"]
            }
          ]
        }
        """;

    private static final String DARK_AUCTION = """
        {
          "id": "DARK_AUCTION",
          "name": "Dark Auction",
          "trigger": "SKYBLOCK_CALENDAR",
          "schedules": [
            {
              "anchor": { "year": 1 },
              "phases": [
                { "days": 0 },
                { "days": 3 }
              ]
            }
          ]
        }
        """;

    private static final String YEAR_OF_THE_PIG = """
        {
          "id": "YEAR_OF_THE_PIG",
          "name": "Year of the Pig",
          "trigger": "SKYBLOCK_CALENDAR",
          "schedules": [
            {
              "anchor": { "year": 479 },
              "phases": [
                { "years": 1 },
                { "years": 11 }
              ]
            }
          ]
        }
        """;

    private static Gson gson() {
        // the string type connect derives, so a fixture binds the way the corpus does
        return GsonSettings.defaults()
            .mutate()
            .withStringType(GsonSettings.StringType.DEFAULT)
            .build()
            .create();
    }

    @Test
    @DisplayName("the mayor election schedule reproduces the election arithmetic to the millisecond")
    void mayorElectionMatchesElection() {
        Event event = gson().fromJson(MAYOR_ELECTION, Event.class);
        Election.Cycle voting = new Election(278).getVoting();
        Event.Occurrence occurrence = event.getOccurrenceAt(1684145700000L).orElseThrow();

        // the same two instants the election reports, asserted against both the literals and the
        // election, so either side drifting fails here
        assertThat(occurrence.getStart(), is(equalTo(1684145700000L)));
        assertThat(occurrence.getEnd(), is(equalTo(1684480500000L)));
        assertThat(occurrence.getStart(), is(equalTo(voting.getStart().getRealTime())));
        assertThat(occurrence.getEnd(), is(equalTo(voting.getEnd().getRealTime())));

        // one run per SkyBlock year, counted from the year voting first opened
        assertThat(occurrence.getIndex(), is(equalTo(278L - Election.FIRST_YEAR)));
    }

    @Test
    @DisplayName("the zoo rotation lands the recorded pet on the recorded visit")
    void travelingZooRotationHoldsItsPhase() {
        Event event = gson().fromJson(TRAVELING_ZOO, Event.class);
        long earlySummer = new SkyBlockDate(505, Season.EARLY_SUMMER, 1, 0).getRealTime();

        // year 505 is recorded as an Elephant summer and a Giraffe winter, so a rotation anchored
        // at year 50 has to reach those two and no others
        Event.Occurrence summer = event.getOccurrenceAt(earlySummer).orElseThrow();
        assertThat(summer.getRotation().orElseThrow(), is(equalTo("ELEPHANT")));
        assertThat(summer.getEnd() - summer.getStart(), is(equalTo(Length.DAY_MS * 3)));

        Event.Occurrence winter = event.getNextOccurrence(summer.getEnd()).orElseThrow();
        assertThat(winter.getRotation().orElseThrow(), is(equalTo("GIRAFFE")));
        assertThat(new SkyBlockDate(winter.getStart()).getYear(), is(equalTo(505)));
        assertThat(new SkyBlockDate(winter.getStart()).getMonth(), is(equalTo(Season.EARLY_WINTER.getMonth())));
    }

    @Test
    @DisplayName("an instantaneous event is reachable only as a next occurrence")
    void zeroLengthRunIsReachableOnlyAsNextOccurrence() {
        Event event = gson().fromJson(DARK_AUCTION, Event.class);
        long anchor = new SkyBlockDate(1, Season.EARLY_SPRING, 1, 0).getRealTime();

        // a zero-length run covers no instant at all, so the walk falls through it into the gap
        assertThat(event.isRunningAt(anchor), is(false));
        assertThat(event.getOccurrenceAt(anchor).isPresent(), is(false));

        Event.Occurrence first = event.getNextOccurrence(anchor).orElseThrow();
        assertThat(first.getStart(), is(equalTo(anchor)));
        assertThat(first.getEnd(), is(equalTo(anchor)));

        Event.Occurrence second = event.getNextOccurrence(first.getStart() + 1).orElseThrow();
        assertThat(second.getStart() - first.getStart(), is(equalTo(Length.DAY_MS * 3)));
    }

    @Test
    @DisplayName("a query behind the anchor folds forward onto a run")
    void anchorIsAPhaseRepresentativeNotAFirstRun() {
        Event event = gson().fromJson(YEAR_OF_THE_PIG, Event.class);
        long year407 = new SkyBlockDate(407, Season.EARLY_SPRING, 1, 0).getRealTime();

        // the anchor names year 479 and year 407 is six cycles behind it, which is the direction
        // truncating division answers backwards
        assertThat(event.isRunningAt(year407), is(true));

        Event.Occurrence occurrence = event.getOccurrenceAt(year407).orElseThrow();
        assertThat(occurrence.getStart(), is(equalTo(year407)));
        assertThat(occurrence.getEnd(), is(equalTo(year407 + Length.YEAR_MS)));
        assertThat(occurrence.getIndex(), is(equalTo(-6L)));

        // a remainder over the twelve-year period rather than a year number, and year 407 leaves
        // the same one
        assertThat(event.getSlot().orElseThrow(), is(equalTo(11)));
    }

}
