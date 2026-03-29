package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.persistence.JpaModel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(name = "bonus_armor_sets")
public class BonusArmorSet implements JpaModel, BuffEffectsModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "helmet_item_id", nullable = false)
    private @NotNull String helmetItemId = "";

    @Column(name = "chestplate_item_id", nullable = false)
    private @NotNull String chestplateItemId = "";

    @Column(name = "leggings_item_id", nullable = false)
    private @NotNull String leggingsItemId = "";

    @Column(name = "boots_item_id", nullable = false)
    private @NotNull String bootsItemId = "";

    @Column(name = "effects", nullable = false)
    private @NotNull ConcurrentMap<String, Double> effects = Concurrent.newMap();

    @Column(name = "buff_effects", nullable = false)
    private @NotNull ConcurrentMap<String, Object> buffEffects = Concurrent.newMap();

    @ManyToOne
    @JoinColumn(name = "helmet_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item helmetItem;

    @ManyToOne
    @JoinColumn(name = "chestplate_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item chestplateItem;

    @ManyToOne
    @JoinColumn(name = "leggings_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item leggingsItem;

    @ManyToOne
    @JoinColumn(name = "boots_item_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Item bootsItem;

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
