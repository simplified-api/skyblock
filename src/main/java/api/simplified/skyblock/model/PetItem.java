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
 * A pet item - the single consumable slotted onto a pet to improve it, one per pet.
 *
 * <p>
 * The table ships no rows today, so a lookup against it resolves nothing; a row would say which pet
 * item it is and what holding it grants.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Pet_Items">Pet Items</a>
 */
@Getter
@Entity
@Table(name = "pet_items")
public class PetItem implements JpaModel {

    /**
     * The pet item's id, matching the item id the wire names on the pet holding it.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the pet item.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Whether the granted values are percentages rather than flat amounts.
     */
    @Column(name = "percentage", nullable = false)
    private boolean percentage;

    /**
     * What the item grants, as {@link Stat.Substitute} references.
     */
    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    /**
     * Negates the {@code percentage} flag.
     *
     * @return {@code true} when the granted values are flat amounts rather than percentages
     */
    public boolean notPercentage() {
        return !this.isPercentage();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        PetItem that = (PetItem) o;

        return this.isPercentage() == that.isPercentage()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getStats(), that.getStats());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.isPercentage(), this.getStats());
    }

}
