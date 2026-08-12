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
 * The stat contribution one {@link Reforge} makes, held apart from the reforge definition so a stat
 * total can be built without the tooltip data.
 *
 * <p>
 * The key is the reforge id itself, so there is one row per reforge. The table is declared and
 * joined but ships no rows today.
 */
@Getter
@Entity
@Table(name = "bonus_reforge_stats")
public class BonusReforgeStat implements JpaModel, BuffEffectsModel {

    /**
     * The reforge this contribution belongs to, and the row's own key; bound from the key
     * {@code reforge}.
     */
    @Id
    @SerializedName("reforge")
    @Column(name = "reforge_id", nullable = false)
    private @NotNull String reforgeId = "";

    /**
     * Flat stat additions the reforge grants, keyed by {@link Stat} id.
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
     * The resolved {@link Reforge} behind the reforge id.
     */
    @ManyToOne
    @JoinColumn(name = "reforge_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Reforge reforge;

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

        BonusReforgeStat that = (BonusReforgeStat) o;

        return Objects.equals(this.getReforgeId(), that.getReforgeId())
            && Objects.equals(this.getEffects(), that.getEffects())
            && Objects.equals(this.getBuffEffects(), that.getBuffEffects());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getReforgeId(), this.getEffects(), this.getBuffEffects());
    }

}
