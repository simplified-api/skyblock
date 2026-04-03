package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.persistence.ForeignIds;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.api.persistence.type.GsonType;
import dev.sbs.minecraftapi.generator.text.ChatFormat;
import dev.sbs.minecraftapi.skyblock.common.Rarity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "enchantments")
public class Enchantment implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private @NotNull Type type = Type.NORMAL;

    @Column(name = "required_level", nullable = false)
    private int requiredLevel = 0;

    @Column(name = "conflict")
    private @NotNull Optional<String> conflict = Optional.empty();

    @SerializedName("categories")
    @Column(name = "categories", nullable = false)
    private @NotNull ConcurrentList<String> categoryIds = Concurrent.newList();

    @SerializedName("items")
    @Column(name = "items", nullable = false)
    private @NotNull ConcurrentList<String> itemIds = Concurrent.newList();

    @Column(name = "levels", nullable = false)
    private @NotNull ConcurrentList<Level> levels = Concurrent.newList();

    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    @SerializedName("mobTypes")
    @Column(name = "mob_types", nullable = false)
    private @NotNull ConcurrentList<String> mobTypeIds = Concurrent.newList();

    @ForeignIds("categoryIds")
    private transient @NotNull ConcurrentList<ItemCategory> categories = Concurrent.newList();

    @ForeignIds("itemIds")
    private transient @NotNull ConcurrentList<Item> items = Concurrent.newList();

    @ForeignIds("mobTypeIds")
    private transient @NotNull ConcurrentList<MobType> mobTypes = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Enchantment that = (Enchantment) o;

        return this.getRequiredLevel() == that.getRequiredLevel()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getType(), that.getType())
            && Objects.equals(this.getConflict(), that.getConflict())
            && Objects.equals(this.getCategoryIds(), that.getCategoryIds())
            && Objects.equals(this.getItemIds(), that.getItemIds())
            && Objects.equals(this.getLevels(), that.getLevels())
            && Objects.equals(this.getStats(), that.getStats())
            && Objects.equals(this.getMobTypeIds(), that.getMobTypeIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getType(), this.getRequiredLevel(), this.getConflict(), this.getCategoryIds(), this.getItemIds(), this.getLevels(), this.getStats(), this.getMobTypeIds());
    }

    @Getter
    @RequiredArgsConstructor
    public enum Type {

        NORMAL(ChatFormat.BLUE, false),
        ULTIMATE(ChatFormat.LIGHT_PURPLE, true);

        private final @NotNull ChatFormat format;
        private final boolean bold;

        @Override
        public String toString() {
            return this.getFormat() + (this.isBold() ? ChatFormat.BOLD.toString() : "");
        }

    }

    @Getter
    @GsonType
    public static class Level {

        private int level = 0;
        @SerializedName("cost")
        private @NotNull Item.Cost applyCost = new Item.Cost();

        public @NotNull Rarity getRarity() {
            return switch (this.getLevel()) {
                case 9, 10 -> Rarity.MYTHIC;
                case 8 -> Rarity.LEGENDARY;
                case 7 -> Rarity.EPIC;
                case 6 -> Rarity.RARE;
                case 5 -> Rarity.UNCOMMON;
                default -> Rarity.COMMON;
            };
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Level that = (Level) o;

            return this.getLevel() == that.getLevel()
                && Objects.equals(this.getApplyCost(), that.getApplyCost());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getLevel(), this.getApplyCost());
        }

    }

}
