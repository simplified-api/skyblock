package api.simplified.skyblock.date;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * A mayor election won by a special mayor - one of the recurring mayors that only stands
 * every eighth SkyBlock year.
 *
 * <p>
 * The voting and term windows are the ordinary {@link Election} windows for the year, so all
 * this adds is the id of the mayor holding it. That id is part of identity here, unlike
 * the year-only identity of a regular election.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Mayor_Election">Mayor Election</a>
 */
@Getter
public class SpecialElection extends Election {

    /**
     * SkyBlock year special mayor voting first opened in, and the earliest year a special election
     * is held in.
     */
    public static final int FIRST_YEAR = 96;

    /**
     * SkyBlock years between one special election and the next.
     */
    public static final int PERIOD_YEARS = 8;

    /**
     * The special mayors that stand in turn, by mayor id, in the order they take office.
     */
    public static final @NotNull ConcurrentList<String> ROTATION = Concurrent.newUnmodifiableList(
        "SHADY_CANDIDATE",
        "DERP_CANDIDATE",
        "JERRY_CANDIDATE"
    );

    /**
     * The known exceptions to {@link #ROTATION}, keyed by the SkyBlock year whose mayor id differs
     * from the one the rotation reaches.
     */
    public static final @NotNull ConcurrentMap<Integer, String> OVERRIDES = Concurrent.newUnmodifiableMap(
        Map.entry(128, "DANTE_CANDIDATE")
    );

    /**
     * Id of the special mayor holding this election, joining to a {@code Mayor} row - a consumer
     * wanting the mayor's own name resolves that row and reads it there.
     */
    private final @NotNull String specialMayorId;

    /**
     * Constructs a new {@code SpecialElection} for the given SkyBlock year and special mayor.
     *
     * @param year the SkyBlock year the election is held in
     * @param specialMayorId the id of the special mayor holding it
     */
    public SpecialElection(int year, @NotNull String specialMayorId) {
        super(year);
        this.specialMayorId = specialMayorId;
    }

    /**
     * Finds the id of the special mayor standing in the given SkyBlock year.
     *
     * <p>
     * {@link #ROTATION} advances one place every {@link #PERIOD_YEARS} years and repeats forever,
     * and {@link #OVERRIDES} answers ahead of it for the years it names.
     *
     * @param year the SkyBlock year to read the rotation at
     * @return the id of the mayor standing that year
     */
    public static @NotNull String mayorIdOf(int year) {
        return OVERRIDES.getOrDefault(year, ROTATION.get(Math.floorMod(Math.floorDiv(year, PERIOD_YEARS), ROTATION.size())));
    }

    /**
     * Finds the next special election due from the current time.
     *
     * @return the earliest special election not behind the current SkyBlock year
     */
    public static @NotNull SpecialElection next() {
        return upcoming(1).getFirst();
    }

    /**
     * Collects the special elections due from the current time.
     *
     * @param next how many special elections to return, clamped to at least one
     * @return the upcoming special elections, in calendar order
     */
    public static @NotNull ConcurrentList<SpecialElection> upcoming(int next) {
        return upcoming(next, new SkyBlockDate(System.currentTimeMillis()));
    }

    /**
     * Collects the special elections due from the given date.
     *
     * <p>
     * One is held every {@link #PERIOD_YEARS} SkyBlock years from {@link #FIRST_YEAR} onwards, and
     * any whose year precedes the given date is skipped.
     *
     * @param next how many special elections to return, clamped to at least one
     * @param fromDate the date to start counting from
     * @return the upcoming special elections, in calendar order
     */
    public static @NotNull ConcurrentList<SpecialElection> upcoming(int next, @NotNull SkyBlockDate fromDate) {
        next = Math.max(next, 1);
        ConcurrentList<SpecialElection> elections = Concurrent.newList();
        int year = FIRST_YEAR;

        while (year < fromDate.getYear())
            year += PERIOD_YEARS;

        while (elections.size() < next) {
            elections.add(new SpecialElection(year, mayorIdOf(year)));
            year += PERIOD_YEARS;
        }

        return elections;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        SpecialElection that = (SpecialElection) o;

        return Objects.equals(this.getSpecialMayorId(), that.getSpecialMayorId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.getSpecialMayorId());
    }

    @Override
    public String toString() {
        return String.format(
            "SpecialElection{specialMayorId='%s', year=%d, voting=%s, term=%s}",
            this.getSpecialMayorId(),
            this.getYear(),
            this.getVoting(),
            this.getTerm()
        );
    }

}
