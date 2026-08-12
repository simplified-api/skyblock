package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
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
 * A stat an {@link Item} grants only in a particular context - while its own stats are being
 * totalled, while it is reforged, while it holds gemstones, or while a given kind of mob is being
 * fought.
 *
 * <p>
 * The three context flags are independent rather than a choice of one, so a single row can count in
 * more than one place. One item can carry several of these rows, which is why the key is generated
 * rather than being the item id. The table is declared and joined but ships no rows today.
 */
@Getter
@Entity
@Table(name = "bonus_item_stats")
public class BonusItemStat implements JpaModel, BuffEffectsModel {

    /**
     * The surrogate key, generated on insert and carrying no game meaning.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    /**
     * The item this row applies to, bound from the key {@code item}.
     */
    @SerializedName("item")
    @Column(name = "item_id", nullable = false)
    private @NotNull String itemId = "";

    /**
     * Whether the row counts when totalling the item's own stats.
     */
    @Column(name = "for_stats", nullable = false)
    private boolean forStats;

    /**
     * Whether the row counts toward the item's reforge contribution.
     */
    @Column(name = "for_reforges", nullable = false)
    private boolean forReforges;

    /**
     * Whether the row counts toward the item's gemstone contribution.
     */
    @Column(name = "for_gems", nullable = false)
    private boolean forGems;

    /**
     * The kind of mob that must be under attack for the row to apply, null when it applies
     * unconditionally - a plain key with no join onto {@link MobType}.
     */
    @Column(name = "required_mob_type_key")
    private @Nullable String requiredMobTypeKey;

    /**
     * Flat stat additions this context grants, keyed by {@link Stat} id.
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

        BonusItemStat that = (BonusItemStat) o;

        return this.getId() == that.getId()
            && this.isForStats() == that.isForStats()
            && this.isForReforges() == that.isForReforges()
            && this.isForGems() == that.isForGems()
            && Objects.equals(this.getItemId(), that.getItemId())
            && Objects.equals(this.getRequiredMobTypeKey(), that.getRequiredMobTypeKey())
            && Objects.equals(this.getEffects(), that.getEffects())
            && Objects.equals(this.getBuffEffects(), that.getBuffEffects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getItemId(), this.isForStats(), this.isForReforges(), this.isForGems(), this.getRequiredMobTypeKey(), this.getEffects(), this.getBuffEffects());
    }

}
