package dev.sbs.minecraftapi.persistence.model;

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

@Getter
@Entity
@Table(name = "bonus_reforge_stats")
public class BonusReforgeStat implements JpaModel, BuffEffectsModel {

    @Id
    @SerializedName("reforge")
    @Column(name = "reforge_id", nullable = false)
    private @NotNull String reforgeId = "";

    @Column(name = "effects", nullable = false)
    private @NotNull ConcurrentMap<String, Double> effects = Concurrent.newMap();

    @Column(name = "buff_effects", nullable = false)
    private @NotNull ConcurrentMap<String, Object> buffEffects = Concurrent.newMap();

    @ManyToOne
    @JoinColumn(name = "reforge_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Reforge reforge;

    @Override
    public @NotNull Map<String, Double> getEffects() {
        return this.effects;
    }

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
