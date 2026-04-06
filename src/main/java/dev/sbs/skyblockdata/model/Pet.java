package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.minecraftapi.generator.text.ChatFormat;
import dev.sbs.minecraftapi.skyblock.common.Rarity;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import dev.simplified.util.StringUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "pets")
public class Pet implements JpaModel {

    public static final @NotNull ConcurrentList<Integer> PET_SCORE = Concurrent.newList(
        10, 25, 50, 75, 100, 130, 175,
        225, 275, 325, 375, 450, 500
    );

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "lowest_rarity", nullable = false)
    private @NotNull Rarity lowestRarity = Rarity.COMMON;

    @SerializedName("skill")
    @Column(name = "skill_id", nullable = false)
    private @NotNull String skillId = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private @NotNull Type type = Type.PET;

    @Column(name = "max_level", nullable = false)
    private int maxLevel = 100;

    @Column(name = "passive", nullable = false)
    private boolean passive = false;

    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

    @Column(name = "abilities", nullable = false)
    private @NotNull ConcurrentList<Ability> abilities = Concurrent.newList();

    @ManyToOne
    @JoinColumn(name = "skill_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Skill skill;

    public @NotNull ConcurrentList<Substitute> getStats(@NotNull Rarity rarity) {
        return this.getStats()
            .stream()
            .filter(stat -> stat.getValues().containsKey(rarity))
            .collect(Concurrent.toUnmodifiableList());
    }

    public @NotNull ConcurrentList<Ability> getAbilities(@NotNull Rarity rarity) {
        return this.getAbilities()
            .stream()
            .filter(ability -> ability.getStats()
                .stream()
                .anyMatch(stat -> stat.getValues().containsKey(rarity))
            )
            .collect(Concurrent.toUnmodifiableList());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Pet that = (Pet) o;

        return this.getMaxLevel() == that.getMaxLevel()
            && this.isPassive() == that.isPassive()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getLowestRarity(), that.getLowestRarity())
            && Objects.equals(this.getSkillId(), that.getSkillId())
            && Objects.equals(this.getType(), that.getType())
            && Objects.equals(this.getStats(), that.getStats())
            && Objects.equals(this.getAbilities(), that.getAbilities());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getLowestRarity(), this.getSkillId(), this.getType(), this.getMaxLevel(), this.isPassive(), this.getStats(), this.getAbilities());
    }

    public enum Type {

        PET,
        MOUNT,
        MORPH,
        GABAGOOL;

        public @NotNull String getName() {
            return StringUtil.capitalizeFully(this.name().replace("_", " "));
        }

    }

    @Getter
    @GsonType
    public static class Ability {

        private @NotNull String name = "";
        private @NotNull String description = "";
        private boolean flatStat = false;
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

        public @NotNull ConcurrentList<Substitute> getStats() {
            return this.stats;
        }

        public @NotNull ConcurrentList<Substitute> getStats(@NotNull Rarity rarity) {
            return this.getStats()
                .stream()
                .filter(stat -> stat.getValues().containsKey(rarity))
                .collect(Concurrent.toUnmodifiableList());
        }

        public @NotNull String getFormattedDescription() {
            // TODO: Populate STAT:X, VALUE:X
            //  Perform a StringUtil.format on description
            //  given the stats
            return this.getDescription();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Ability that = (Ability) o;

            return this.isFlatStat() == that.isFlatStat()
                && Objects.equals(this.getName(), that.getName())
                && Objects.equals(this.getDescription(), that.getDescription())
                && Objects.equals(this.getStats(), that.getStats());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getName(), this.getDescription(), this.isFlatStat(), this.getStats());
        }

    }

    @Getter
    @GsonType
    public static class Substitute {

        private @NotNull String id = "";
        private int precision = 0;
        @Enumerated(EnumType.STRING)
        private @NotNull Stat.Type type = Stat.Type.NONE;
        @Enumerated(EnumType.STRING)
        private @NotNull ChatFormat format = ChatFormat.GREEN;
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentMap<Rarity, Value> values = Concurrent.newMap();

        public @NotNull ConcurrentMap<Rarity, Value> getValues() {
            return this.values;
        }

        public @NotNull Optional<Stat> getStat() {
            return dev.sbs.minecraftapi.MinecraftApi.getRepository(Stat.class)
                .findFirst(Stat::getId, this.getId());
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Substitute that = (Substitute) o;

            return this.getPrecision() == that.getPrecision()
                && Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getType(), that.getType())
                && Objects.equals(this.getFormat(), that.getFormat())
                && Objects.equals(this.getValues(), that.getValues());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getPrecision(), this.getType(), this.getFormat(), this.getValues());
        }

        @Getter
        @GsonType
        public static class Value {

            private double base = 0.0;
            private double scalar = 0.0;

        }

    }

}