package dev.simplified.skyblock.date;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@NoArgsConstructor
public class Election {

    private int year;

    public Election(int year) {
        this.year = year;
    }

    /**
     * Window in which this election's year votes, opening late summer and closing the following late
     * spring
     */
    public @NotNull Cycle getVoting() {
        return new Cycle(
            new SkyBlockDate(this.getYear(), Season.LATE_SUMMER, 27, 0),
            new SkyBlockDate(this.getYear() + 1, Season.LATE_SPRING, 27, 0)
        );
    }

    /**
     * Window in which the elected mayor holds office, running from the close of voting for a full year
     */
    public @NotNull Cycle getTerm() {
        return new Cycle(
            new SkyBlockDate(this.getYear() + 1, Season.LATE_SPRING, 27, 0),
            new SkyBlockDate(this.getYear() + 2, Season.LATE_SPRING, 27, 0)
        );
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

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Cycle {

        private final @NotNull SkyBlockDate start;
        private final @NotNull SkyBlockDate end;

        @Override
        public String toString() {
            return String.format("Cycle{start=%s, end=%s}", this.getStart(), this.getEnd());
        }

    }

}
