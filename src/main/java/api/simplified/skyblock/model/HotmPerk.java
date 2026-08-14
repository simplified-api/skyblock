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
 * One node of the Heart of the Mountain, the mining skill tree unlocked in the Dwarven Mines and
 * levelled with powder.
 *
 * <p>
 * A node is its identity and how far it levels. What one grants is held on {@link Buff}, under a
 * {@code PERK} subject naming this id, so a contribution is read from there rather than from a
 * column here.
 *
 * <p>
 * The powder a node costs is not modelled, and no nodes are supplied today, so a lookup finds
 * nothing until the data source carries them.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Heart_of_the_Mountain">Heart of the Mountain</a>
 */
@Getter
@Entity
@Table(name = "hotm_perks")
public class HotmPerk implements JpaModel {

    /**
     * The perk's id, matching the key a member's Heart of the Mountain tree stores it under.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The perk's display name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * How far the perk can be levelled, {@code 0} for the one-shot nodes that are simply unlocked.
     */
    @Column(name = "max_level", nullable = false)
    private int maxLevel = 0;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        HotmPerk that = (HotmPerk) o;

        return this.getMaxLevel() == that.getMaxLevel()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getMaxLevel());
    }

}
