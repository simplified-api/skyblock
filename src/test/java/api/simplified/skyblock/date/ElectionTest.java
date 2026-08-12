package api.simplified.skyblock.date;

import dev.simplified.collection.ConcurrentList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Covers the election cycles, which are derived from the year rather than stored.
 */
class ElectionTest {

    @Test
    @DisplayName("election cycles compute the same bounds the hook used to store")
    void computesElectionCycles() {
        Election election = new Election(278);

        // captured off the identical copy of this class in the hypixel module before its hook was
        // removed, so these are pre-change values rather than a restatement of the expressions
        assertThat(election.getVoting().getStart().getRealTime(), is(equalTo(1684145700000L)));
        assertThat(election.getVoting().getEnd().getRealTime(), is(equalTo(1684480500000L)));
        assertThat(election.getTerm().getStart().getRealTime(), is(equalTo(1684480500000L)));
        assertThat(election.getTerm().getEnd().getRealTime(), is(equalTo(1684926900000L)));

        // a term begins the moment voting closes
        assertThat(election.getTerm().getStart().getRealTime(),
            is(equalTo(election.getVoting().getEnd().getRealTime())));
    }

    @Test
    @DisplayName("two elections of one year are equal and hash alike")
    void electionIdentityIsItsYear() {
        // Cycle declares no equals, so folding the derived cycles into identity made this false
        assertThat(new Election(278), is(equalTo(new Election(278))));
        assertThat(new Election(278).hashCode(), is(equalTo(new Election(278).hashCode())));
        assertThat(new Election(278), is(not(equalTo(new Election(279)))));
    }

    @Test
    @DisplayName("a special election carries its mayor into identity")
    void specialElectionIdentityIncludesItsMayor() {
        assertThat(new SpecialElection(278, "JERRY_CANDIDATE"), is(equalTo(new SpecialElection(278, "JERRY_CANDIDATE"))));
        assertThat(new SpecialElection(278, "JERRY_CANDIDATE"), is(not(equalTo(new SpecialElection(278, "SHADY_CANDIDATE")))));
        assertThat(new SpecialElection(278, "JERRY_CANDIDATE").getVoting().getStart().getRealTime(),
            is(equalTo(1684145700000L)));
    }

    @Test
    @DisplayName("a term closes after the voting that opened it")
    void electionWindowsRunForward() {
        Election election = new Election(Election.FIRST_YEAR);
        SpecialElection special = new SpecialElection(
            SpecialElection.FIRST_YEAR,
            SpecialElection.mayorIdOf(SpecialElection.FIRST_YEAR)
        );

        assertThat(election.getVoting().getEnd().getRealTime(),
            is(greaterThan(election.getVoting().getStart().getRealTime())));
        assertThat(election.getTerm().getEnd().getRealTime(),
            is(greaterThan(election.getVoting().getEnd().getRealTime())));

        assertThat(special.getVoting().getEnd().getRealTime(),
            is(greaterThan(special.getVoting().getStart().getRealTime())));
        assertThat(special.getTerm().getEnd().getRealTime(),
            is(greaterThan(special.getVoting().getEnd().getRealTime())));
    }

    @Test
    @DisplayName("the special mayor rotation reproduces the years it has already run")
    void rotationMatchesTheRecordedHistory() {
        // the observed history, which is what fixes the rotation's phase
        assertThat(SpecialElection.mayorIdOf(96), is(equalTo("SHADY_CANDIDATE")));
        assertThat(SpecialElection.mayorIdOf(104), is(equalTo("DERP_CANDIDATE")));
        assertThat(SpecialElection.mayorIdOf(112), is(equalTo("JERRY_CANDIDATE")));
        assertThat(SpecialElection.mayorIdOf(120), is(equalTo("SHADY_CANDIDATE")));
        assertThat(SpecialElection.mayorIdOf(136), is(equalTo("JERRY_CANDIDATE")));
        assertThat(SpecialElection.mayorIdOf(144), is(equalTo("SHADY_CANDIDATE")));

        // year 128 stood outside the rotation, which is why the overrides are carried beside it
        assertThat(SpecialElection.mayorIdOf(128), is(equalTo("DANTE_CANDIDATE")));
        assertThat(SpecialElection.ROTATION, hasItem(SpecialElection.mayorIdOf(120)));
        assertThat(SpecialElection.ROTATION, not(hasItem(SpecialElection.mayorIdOf(128))));
    }

    @Test
    @DisplayName("a forecast starts at the given year and never behind the first election")
    void upcomingCountsForwardFromTheDate() {
        ConcurrentList<? extends Election> fromLaunch = Election.upcoming(3, new SkyBlockDate(1, Season.EARLY_SPRING, 1, 0));

        // one election a year, and none before the year voting first opened
        assertThat(fromLaunch.size(), is(equalTo(3)));
        assertThat(fromLaunch.getFirst().getYear(), is(equalTo(Election.FIRST_YEAR)));
        assertThat(fromLaunch.getLast().getYear(), is(equalTo(Election.FIRST_YEAR + 2)));

        ConcurrentList<? extends Election> fromLater = Election.upcoming(2, new SkyBlockDate(278, Season.EARLY_SPRING, 1, 0));
        assertThat(fromLater.getFirst().getYear(), is(equalTo(278)));

        // a count below one still answers with one
        assertThat(Election.upcoming(0, new SkyBlockDate(278, Season.EARLY_SPRING, 1, 0)).size(), is(equalTo(1)));
    }

    @Test
    @DisplayName("special elections forecast every eighth year with the mayor of that year")
    void upcomingSpecialElectionsStepThePeriod() {
        ConcurrentList<SpecialElection> upcoming = SpecialElection.upcoming(3, new SkyBlockDate(1, Season.EARLY_SPRING, 1, 0));

        assertThat(upcoming.getFirst().getYear(), is(equalTo(SpecialElection.FIRST_YEAR)));
        assertThat(upcoming.get(1).getYear(), is(equalTo(SpecialElection.FIRST_YEAR + SpecialElection.PERIOD_YEARS)));
        assertThat(upcoming.getLast().getYear(), is(equalTo(SpecialElection.FIRST_YEAR + (SpecialElection.PERIOD_YEARS * 2))));

        // each carries the mayor the rotation reaches for its own year
        for (SpecialElection election : upcoming)
            assertThat(election.getSpecialMayorId(), is(equalTo(SpecialElection.mayorIdOf(election.getYear()))));
    }

}
