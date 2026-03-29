package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.persistence.JpaModel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(name = "bonus_item_stats")
public class BonusItemStat implements JpaModel, BuffEffectsModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    @SerializedName("item")
    @Column(name = "item_id", nullable = false)
    private @NotNull String itemId = "";

    @Column(name = "for_stats", nullable = false)
    private boolean forStats;

    @Column(name = "for_reforges", nullable = false)
    private boolean forReforges;

    @Column(name = "for_gems", nullable = false)
    private boolean forGems;

    @Column(name = "required_mob_type_key")
    private @Nullable String requiredMobTypeKey;

    @Column(name = "effects", nullable = false)
    private @NotNull ConcurrentMap<String, Double> effects = Concurrent.newMap();

    @Column(name = "buff_effects", nullable = false)
    private @NotNull ConcurrentMap<String, Object> buffEffects = Concurrent.newMap();

    @ManyToOne
    @JoinColumn(name = "item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item item;

    public boolean hasRequiredMobType() {
        return this.requiredMobTypeKey != null;
    }

    public boolean noRequiredMobType() {
        return !this.hasRequiredMobType();
    }

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
