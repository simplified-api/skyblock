package api.simplified.skyblock.model;

import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A named SkyBlock year - the Year of the Pig, the Year of the Seal, the Year of the Witch. Each
 * recurs once every twelve SkyBlock years on a zodiac-style rotation and brings its own event with
 * it.
 */
@Getter
@Entity
@Table(name = "zodiac_events")
public class ZodiacEvent implements JpaModel {

    /**
     * The event's id, spelled as the year's name - {@code YEAR_OF_THE_SEAL}.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the event.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The SkyBlock year the event first ran, which is what fixes its slot in the rotation.
     */
    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    /**
     * The event's slot in the twelve-year rotation, derived as the release year's remainder - any
     * SkyBlock year leaving the same remainder is a year this event runs.
     *
     * <p>
     * It is a remainder in {@code 0..11} and not a year number, so comparing it against a real
     * SkyBlock year is the mistake to avoid.
     */
    public int getRecurringYear() {
        return this.releaseYear % 12;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        ZodiacEvent that = (ZodiacEvent) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && this.getReleaseYear() == that.getReleaseYear();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getReleaseYear());
    }

}