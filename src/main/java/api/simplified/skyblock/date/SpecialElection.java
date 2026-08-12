package api.simplified.skyblock.date;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A mayor election won by a special mayor - one of the recurring mayors that only stands
 * every eighth SkyBlock year.
 *
 * <p>
 * The voting and term windows are the ordinary {@link Election} windows for the year, so all
 * this adds is the name of the mayor holding it. That name is part of identity here, unlike
 * the year-only identity of a regular election.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Mayor_Election">Mayor Election</a>
 */
@Getter
public class SpecialElection extends Election {

    /**
     * Name of the special mayor holding this election, drawn from
     * {@link SkyBlockDate#SPECIAL_MAYOR_CYCLE}.
     */
    private final @NotNull String specialMayor;

    /**
     * Constructs a new {@code SpecialElection} for the given SkyBlock year and special mayor.
     *
     * @param year the SkyBlock year the election is held in
     * @param specialMayor the name of the special mayor holding it
     */
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
