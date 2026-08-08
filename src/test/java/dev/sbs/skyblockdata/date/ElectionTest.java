package dev.sbs.skyblockdata.date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
        assertThat(new SpecialElection(278, "Jerry"), is(equalTo(new SpecialElection(278, "Jerry"))));
        assertThat(new SpecialElection(278, "Jerry"), is(not(equalTo(new SpecialElection(278, "Scorpius")))));
        assertThat(new SpecialElection(278, "Jerry").getVoting().getStart().getRealTime(),
            is(equalTo(1684145700000L)));
    }

}
