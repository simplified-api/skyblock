package dev.sbs.skyblockdata.date;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
public class SpecialElection extends Election {

    private final @NotNull String specialMayor;

    public SpecialElection(int year, @NotNull String specialMayor) {
        super(year);
        this.specialMayor = specialMayor;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SpecialElection that = (SpecialElection) o;

        return Objects.equals(this.getSpecialMayor(), that.getSpecialMayor());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getSpecialMayor());
    }

    @Override
    public String toString() {
        return String.format(
            "SpecialElection{specialMayor='%s', year=%d, voting=%s, term=%s}",
            this.getSpecialMayor(),
            this.getYear(),
            this.getVoting(),
            this.getTerm()
        );
    }

}
