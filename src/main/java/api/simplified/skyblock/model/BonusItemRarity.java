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
 * The stat contribution an {@link Item} makes purely on account of its rarity, separate from the
 * stats printed on the item itself.
 *
 * <p>
 * The table is declared and joined but ships no rows today.
 */
@Getter
@Entity
@Table(name = "bonus_item_rarities")
public class BonusItemRarity implements JpaModel, BuffEffectsModel {

    /**
     * The item this contribution belongs to, and the row's own key; bound from the key
     * {@code item}.
     */
    @Id
    @SerializedName("item")
    @Column(name = "item_id", nullable = false)
    private @NotNull String itemId = "";

    /**
     * Flat stat additions the rarity grants, keyed by {@link Stat} id.
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
     * The resolved {@link Item} behind the item id.
     */
    @ManyToOne
    @JoinColumn(name = "item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item item;

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

        BonusItemRarity that = (BonusItemRarity) o;

        return Objects.equals(this.getItemId(), that.getItemId())
            && Objects.equals(this.getEffects(), that.getEffects())
            && Objects.equals(this.getBuffEffects(), that.getBuffEffects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getItemId(), this.getEffects(), this.getBuffEffects());
    }

}
