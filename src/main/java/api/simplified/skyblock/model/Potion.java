package api.simplified.skyblock.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A potion effect - one brewable or granted buff with a level ladder, whose stats apply for as long
 * as the effect is active.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Potions">Potions</a>
 */
@Getter
@Entity
@Table(name = "potions")
public class Potion implements JpaModel {

    /**
     * The effect's id, matching the key the wire uses under a member's active effects.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the effect.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Template text for the effect's tooltip, carrying the substitution placeholders a
     * {@link Keyword} and the substitute values fill in.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * Whether the effect is a buff rather than a debuff.
     */
    @Column(name = "buff", nullable = false)
    private boolean buff;

    /**
     * Whether a member can brew the effect, as opposed to only receiving it from an item or an event.
     */
    @Column(name = "brewable", nullable = false)
    private boolean brewable;

    /**
     * What the effect grants, as {@link Stat.Substitute} references whose values are keyed by the
     * effect's level.
     */
    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Potion that = (Potion) o;

        return this.isBuff() == that.isBuff()
            && this.isBrewable() == that.isBrewable()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getStats(), that.getStats());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.isBuff(), this.isBrewable(), this.getStats());
    }

}