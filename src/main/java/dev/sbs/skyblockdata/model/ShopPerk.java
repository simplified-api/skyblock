package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.ForeignIds;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "shop_perks")
public class ShopPerk implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    @SerializedName("regions")
    @Column(name = "regions", nullable = false)
    private @NotNull ConcurrentList<String> regionIds = Concurrent.newList();

    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    @Column(name = "unlocks", nullable = false)
    private @NotNull ConcurrentList<Unlock> unlocks = Concurrent.newList();

    @ForeignIds("regionIds")
    private transient @NotNull ConcurrentList<Region> regions = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        ShopPerk that = (ShopPerk) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getRegionIds(), that.getRegionIds())
            && Objects.equals(this.getStats(), that.getStats())
            && Objects.equals(this.getUnlocks(), that.getUnlocks());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getRegionIds(), this.getStats(), this.getUnlocks());
    }

    @Getter
    @GsonType
    public static class Unlock {

        private int tier;
        private @NotNull Item.Cost cost = new Item.Cost();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Unlock that = (Unlock) o;

            return this.getTier() == that.getTier()
                && Objects.equals(this.getCost(), that.getCost());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getTier(), this.getCost());
        }

    }

}