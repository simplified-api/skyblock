package api.simplified.skyblock.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * The full-set bonus of a four-piece armour set - the extra stats granted only while helmet,
 * chestplate, leggings and boots of the same set are worn together.
 *
 * <p>
 * The table is declared and joined but ships no rows today, so a lookup against it resolves nothing
 * until the set bonuses are filled in.
 */
@Getter
@Entity
@Table(name = "bonus_armor_sets")
public class BonusArmorSet implements JpaModel, BuffEffectsModel {

    /**
     * The set's own id.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The set's display name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The id of the helmet that must be worn for the bonus to apply.
     */
    @Column(name = "helmet_item_id", nullable = false)
    private @NotNull String helmetItemId = "";

    /**
     * The id of the chestplate that must be worn for the bonus to apply.
     */
    @Column(name = "chestplate_item_id", nullable = false)
    private @NotNull String chestplateItemId = "";

    /**
     * The id of the leggings that must be worn for the bonus to apply.
     */
    @Column(name = "leggings_item_id", nullable = false)
    private @NotNull String leggingsItemId = "";

    /**
     * The id of the boots that must be worn for the bonus to apply.
     */
    @Column(name = "boots_item_id", nullable = false)
    private @NotNull String bootsItemId = "";

    /**
     * Flat stat additions the full set grants, keyed by {@link Stat} id.
     */
    @Column(name = "effects", nullable = false)
    private @NotNull ConcurrentMap<String, Double> effects = Concurrent.newMap();

    /**
     * Conditional or non-numeric effects keyed by name, typed loosely because the payload shape
     * differs per effect.
     */
    @Column(name = "buff_effects", nullable = false)
    private @NotNull ConcurrentMap<String, Object> buffEffects = Concurrent.newMap();

    /**
     * The resolved {@link Item} behind the helmet id.
     */
    @ManyToOne
    @JoinColumn(name = "helmet_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item helmetItem;

    /**
     * The resolved {@link Item} behind the chestplate id.
     */
    @ManyToOne
    @JoinColumn(name = "chestplate_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item chestplateItem;

    /**
     * The resolved {@link Item} behind the leggings id.
     */
    @ManyToOne
    @JoinColumn(name = "leggings_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item leggingsItem;

    /**
     * The resolved {@link Item} behind the boots id.
     */
    @ManyToOne
    @JoinColumn(name = "boots_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item bootsItem;

    /** {@inheritDoc} */
    @Override
    public @NotNull Map<String, Double> getEffects() {
        return this.effects;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Map<String, Object> getBuffEffects() {
        return this.buffEffects;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        BonusArmorSet that = (BonusArmorSet) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getHelmetItemId(), that.getHelmetItemId())
            && Objects.equals(this.getChestplateItemId(), that.getChestplateItemId())
            && Objects.equals(this.getLeggingsItemId(), that.getLeggingsItemId())
            && Objects.equals(this.getBootsItemId(), that.getBootsItemId())
            && Objects.equals(this.getEffects(), that.getEffects())
            && Objects.equals(this.getBuffEffects(), that.getBuffEffects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getHelmetItemId(), this.getChestplateItemId(), this.getLeggingsItemId(), this.getBootsItemId(), this.getEffects(), this.getBuffEffects());
    }

}
