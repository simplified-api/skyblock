package api.simplified.skyblock.date;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One mayor election, identified by the SkyBlock year it is held in.
 *
 * <p>
 * Voting opens on late summer 27 of year N and closes on late spring 27 of year N+1. The
 * winning mayor's term runs from that close for a full SkyBlock year, so {@code term.start}
 * is exactly {@code voting.end}. Both windows are computed on demand from the year, which is
 * what lets gson bind the no-argument constructor without leaving a half-built object behind.
 *
 * <p>
 * Identity is the year alone. The derived cycles are deliberately outside {@code equals} and
 * {@code hashCode} - a {@link Cycle} declares no equality of its own, so folding them in made
 * two elections of the same year compare unequal.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Mayor_Election">Mayor Election</a>
 */
@Getter
@NoArgsConstructor
public class Election {

    /**
     * SkyBlock year mayor voting first opened in, and the earliest year an election is held in.
     */
    public static final int FIRST_YEAR = 88;

    /**
     * SkyBlock year this election is held in, and the whole of its identity.
     */
    private int year;

    /**
     * Constructs a new {@code Election} for the given SkyBlock year.
     *
     * @param year the SkyBlock year the election is held in
     */
    public Election(int year) {
        this.year = year;
    }

    /**
     * Window in which this election's votes are cast, opening late summer 27 of its year and
     * closing late spring 27 of the next.
     */
    public @NotNull Cycle getVoting() {
        return new Cycle(
            new SkyBlockDate(this.getYear(), Season.LATE_SUMMER, 27, 0),
            new SkyBlockDate(this.getYear() + 1, Season.LATE_SPRING, 27, 0)
        );
    }

    /**
     * Window in which the elected mayor holds office, running from the close of voting for a
     * full SkyBlock year.
     */
    public @NotNull Cycle getTerm() {
        return new Cycle(
            new SkyBlockDate(this.getYear() + 1, Season.LATE_SPRING, 27, 0),
            new SkyBlockDate(this.getYear() + 2, Season.LATE_SPRING, 27, 0)
        );
    }

    /**
     * Finds the next election due from the current time.
     *
     * @return the earliest election not behind the current SkyBlock year
     */
    public static @NotNull Election next() {
        return upcoming(1).getFirst();
    }

    /**
     * Collects the elections due from the current time.
     *
     * @param next how many elections to return, clamped to at least one
     * @return the upcoming elections, in calendar order
     */
    public static @NotNull ConcurrentList<? extends Election> upcoming(int next) {
        return upcoming(next, new SkyBlockDate(System.currentTimeMillis()));
    }

    /**
     * Collects the elections due from the given date.
     *
     * <p>
     * One election is held every SkyBlock year from {@link #FIRST_YEAR} onwards, and any whose year
     * precedes the given date is skipped.
     *
     * <p>
     * The result is a forecast to read rather than a list to add to, which is why its element type
     * is bounded: {@link SpecialElection} answers the same question about the years it stands in and
     * returns its own kind.
     *
     * @param next how many elections to return, clamped to at least one
     * @param fromDate the date to start counting from
     * @return the upcoming elections, in calendar order
     */
    public static @NotNull ConcurrentList<? extends Election> upcoming(int next, @NotNull SkyBlockDate fromDate) {
        next = Math.max(next, 1);
        ConcurrentList<Election> elections = Concurrent.newList();

        for (int year = Math.max(FIRST_YEAR, fromDate.getYear()); elections.size() < next; year++)
            elections.add(new Election(year));

        return elections;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Election election = (Election) o;

        return this.getYear() == election.getYear();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getYear());
    }

    @Override
    public String toString() {
        return String.format("Election{year=%d, voting=%s, term=%s}", this.getYear(), this.getVoting(), this.getTerm());
    }

    /**
     * A span of the SkyBlock calendar, given as the two dates that bound it.
     *
     * <p>
     * A cycle carries no equality of its own, so two cycles are distinct even when their
     * endpoints are the same date.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Cycle {

        /**
         * Date the window opens on.
         */
        private final @NotNull SkyBlockDate start;

        /**
         * Date the window closes on.
         */
        private final @NotNull SkyBlockDate end;

        @Override
        public String toString() {
            return String.format("Cycle{start=%s, end=%s}", this.getStart(), this.getEnd());
        }

    }

}
