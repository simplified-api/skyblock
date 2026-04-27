package dev.sbs.skyblockdata.date;

import dev.simplified.gson.PostInit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@NoArgsConstructor
public class Election implements PostInit {

    private int year;
    private transient Cycle voting;
    private transient Cycle term;

    public Election(int year) {
        this.year = year;
        this.postInit();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Election election = (Election) o;

        return this.getYear() == election.getYear()
            && Objects.equals(this.getVoting(), election.getVoting())
            && Objects.equals(this.getTerm(), election.getTerm());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getYear(), this.getVoting(), this.getTerm());
    }

    @Override
    public void postInit() {
        this.voting = new Cycle(
            new SkyBlockDate(this.getYear(), Season.LATE_SUMMER, 27, 0),
            new SkyBlockDate(this.getYear() + 1, Season.LATE_SPRING, 27, 0)
        );
        this.term = new Cycle(
            new SkyBlockDate(this.getYear() + 1, Season.LATE_SPRING, 27, 0),
            new SkyBlockDate(this.getYear() + 2, Season.LATE_SPRING, 27, 0)
        );
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
