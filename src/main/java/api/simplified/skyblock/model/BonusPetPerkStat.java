package api.simplified.skyblock.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * The stat one named pet ability contributes, optionally gated on a held pet item or on the kind of
 * mob being fought.
 *
 * <p>
 * A pet has several abilities, so a pet has several of these rows and the key is generated rather
 * than being the pet id. The table is declared and joined but ships no rows today.
 */
@Getter
@Entity
@Table(name = "bonus_pet_ability_stats")
public class BonusPetAbilityStat implements JpaModel, BuffEffectsModel {

    /**
     * The surrogate key, generated on insert and carrying no game meaning.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    /**
     * The pet whose ability this is.
     */
    @Column(name = "pet_id", nullable = false)
    private @NotNull String petId = "";

    /**
     * Which of that pet's abilities the row describes, matched by name against the pet's declared
     * abilities rather than joined by id.
     */
    @Column(name = "ability_name", nullable = false)
    private @NotNull String abilityName = "";

    /**
     * Whether the granted values are percentages rather than flat amounts.
     */
    @Column(name = "percentage", nullable = false)
    private boolean percentage;

    /**
     * The pet item that must be equipped for the row to apply, null when it applies
     * unconditionally.
     */
    @Column(name = "required_item_id")
    private @Nullable String requiredItemId;

    /**
     * The kind of mob that must be under attack for the row to apply, null when it applies
     * unconditionally - a plain key with no join onto {@link MobType}.
     */
    @Column(name = "required_mob_type_key")
    private @Nullable String requiredMobTypeKey;

    /**
     * Flat stat additions the ability grants, keyed by {@link Stat} id.
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
     * The resolved {@link Item} behind the required item id, null when the row is ungated.
     */
    @ManyToOne
    @JoinColumn(name = "required_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Item requiredItem;

    /**
     * Whether the granted values are flat amounts rather than percentages.
     */
    public boolean notPercentage() {
        return !this.isPercentage();
    }

    /**
     * Whether the row is gated on a held pet item.
     */
    public boolean hasRequiredItem() {
        return this.requiredItemId != null;
    }

    /**
     * Whether the row applies with no pet item held.
     */
    public boolean noRequiredItem() {
        return !this.hasRequiredItem();
    }

    /**
     * Whether the row is gated on a kind of mob.
     */
    public boolean hasRequiredMobType() {
        return this.requiredMobTypeKey != null;
    }

    /**
     * Whether the row applies whatever is being fought.
     */
    public boolean noRequiredMobType() {
        return !this.hasRequiredMobType();
    }

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

        BonusPetAbilityStat that = (BonusPetAbilityStat) o;

        return this.getId() == that.getId()
            && this.isPercentage() == that.isPercentage()
            && Objects.equals(this.getPetId(), that.getPetId())
            && Objects.equals(this.getAbilityName(), that.getAbilityName())
            && Objects.equals(this.getRequiredItemId(), that.getRequiredItemId())
            && Objects.equals(this.getRequiredMobTypeKey(), that.getRequiredMobTypeKey())
            && Objects.equals(this.getEffects(), that.getEffects())
            && Objects.equals(this.getBuffEffects(), that.getBuffEffects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getPetId(), this.getAbilityName(), this.isPercentage(), this.getRequiredItemId(), this.getRequiredMobTypeKey(), this.getEffects(), this.getBuffEffects());
    }

}
