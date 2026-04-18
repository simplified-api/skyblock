package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.minecraftapi.skyblock.common.Rarity;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.ForeignIds;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "reforges")
public class Reforge implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "stone_id")
    private @NotNull Optional<String> stoneId = Optional.empty();

    @Column(name = "required_level", nullable = false)
    private int requiredLevel = 0;

    @SerializedName("categories")
    @Column(name = "categories", nullable = false)
    private @NotNull ConcurrentList<String> categoryIds = Concurrent.newList();

    @SerializedName("items")
    @Column(name = "items", nullable = false)
    private @NotNull ConcurrentList<String> itemIds = Concurrent.newList();

    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "stone_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Item stone;

    @ForeignIds("categoryIds")
    private transient @NotNull ConcurrentList<ItemCategory> categories = Concurrent.newList();

    @ForeignIds("itemIds")
    private transient @NotNull ConcurrentList<Item> items = Concurrent.newList();

    public @NotNull Optional<Item> getStone() {
        return Optional.ofNullable(this.stone);
    }

    public boolean hasStone() {
        return this.stoneId.isPresent();
    }

    public boolean notStone() {
        return !this.hasStone();
    }

    public @NotNull ConcurrentList<Substitute> getStats(@NotNull Rarity rarity) {
        return this.getStats()
            .stream()
            .filter(stat -> stat.getValues().containsKey(rarity))
            .collect(Concurrent.toUnmodifiableList());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Reforge that = (Reforge) o;

        return this.getRequiredLevel() == that.getRequiredLevel()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getStoneId(), that.getStoneId())
            && Objects.equals(this.getCategoryIds(), that.getCategoryIds())
            && Objects.equals(this.getItemIds(), that.getItemIds())
            && Objects.equals(this.getStats(), that.getStats());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getStoneId(), this.getRequiredLevel(), this.getCategoryIds(), this.getItemIds(), this.getStats());
    }

    @Getter
    @GsonType
    public static class Substitute {

        private @NotNull String id = "";
        private @NotNull ConcurrentMap<Rarity, Double> values = Concurrent.newMap();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Substitute that = (Substitute) o;

            return Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getValues(), that.getValues());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getValues());
        }

    }

}