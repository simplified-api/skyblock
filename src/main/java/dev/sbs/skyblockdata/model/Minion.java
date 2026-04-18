package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "minions")
public class Minion implements JpaModel {

    public static final @NotNull ConcurrentList<Integer> UNIQUE_CRAFTS = Concurrent.newList(
        0, 5, 15, 30, 50, 75,
        100, 125, 150, 175, 200, 225, 250, 275, 300, 350,
        400, 450, 500, 550, 600, 650, 700, 750, 800, 850
    );

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @SerializedName("collection")
    @Column(name = "collection_id", nullable = false)
    private @NotNull String collectionId = "";

    @Column(name = "tiers", nullable = false)
    private @NotNull ConcurrentList<Tier> tiers = Concurrent.newList();

    @ManyToOne
    @JoinColumn(name = "collection_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Collection collection;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Minion that = (Minion) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getCollectionId(), that.getCollectionId())
            && Objects.equals(this.getTiers(), that.getTiers());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getCollectionId(), this.getTiers());
    }

    @Getter
    @GsonType
    public static class Tier {

        private int tier;
        private double speed;
        @SerializedName("item")
        private @NotNull String itemId = "";
        private @NotNull Item.Cost upgradeCost = new Item.Cost();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Tier that = (Tier) o;

            return this.getTier() == that.getTier()
                && this.getSpeed() == that.getSpeed()
                && Objects.equals(this.getItemId(), that.getItemId())
                && Objects.equals(this.getUpgradeCost(), that.getUpgradeCost());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getTier(), this.getSpeed(), this.getItemId(), this.getUpgradeCost());
        }

    }

}