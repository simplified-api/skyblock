package dev.sbs.minecraftapi.persistence.model;

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
@Table(name = "bonus_pet_ability_stats")
public class BonusPetAbilityStat implements JpaModel, BuffEffectsModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "pet_id", nullable = false)
    private @NotNull String petId = "";

    @Column(name = "ability_name", nullable = false)
    private @NotNull String abilityName = "";

    @Column(name = "percentage", nullable = false)
    private boolean percentage;

    @Column(name = "required_item_id")
    private @Nullable String requiredItemId;

    @Column(name = "required_mob_type_key")
    private @Nullable String requiredMobTypeKey;

    @Column(name = "effects", nullable = false)
    private @NotNull ConcurrentMap<String, Double> effects = Concurrent.newMap();

    @Column(name = "buff_effects", nullable = false)
    private @NotNull ConcurrentMap<String, Object> buffEffects = Concurrent.newMap();

    @ManyToOne
    @JoinColumn(name = "required_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Item requiredItem;

    public boolean notPercentage() {
        return !this.isPercentage();
    }

    public boolean hasRequiredItem() {
        return this.requiredItemId != null;
    }

    public boolean noRequiredItem() {
        return !this.hasRequiredItem();
    }

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
