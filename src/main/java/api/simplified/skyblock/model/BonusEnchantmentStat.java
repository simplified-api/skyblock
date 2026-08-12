package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
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
 * The stat contribution one {@link Enchantment} makes to a member's totals, held apart from the
 * enchantment definition because it is an input to a calculation rather than a tooltip.
 *
 * <p>
 * The key is the enchantment id itself, so there is one row per enchantment and never one per level.
 * The table is declared and joined but ships no rows today.
 */
@Getter
@Entity
@Table(name = "bonus_enchantment_stats")
public class BonusEnchantmentStat implements JpaModel, BuffEffectsModel {

    /**
     * The enchantment this contribution belongs to, and the row's own key; bound from the key
     * {@code enchantment}.
     */
    @Id
    @SerializedName("enchantment")
    @Column(name = "enchantment_id", nullable = false)
    private @NotNull String enchantmentId = "";

    /**
     * Flat stat additions the enchantment grants, keyed by {@link Stat} id.
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
     * The resolved {@link Enchantment} behind the enchantment id.
     */
    @ManyToOne
    @JoinColumn(name = "enchantment_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Enchantment enchantment;

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

        BonusEnchantmentStat that = (BonusEnchantmentStat) o;

        return Objects.equals(this.getEnchantmentId(), that.getEnchantmentId())
            && Objects.equals(this.getEffects(), that.getEffects())
            && Objects.equals(this.getBuffEffects(), that.getBuffEffects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getEnchantmentId(), this.getEffects(), this.getBuffEffects());
    }

}
