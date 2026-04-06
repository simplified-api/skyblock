package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.minecraftapi.MinecraftApi;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(name = "skills")
public class Skill implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    @Column(name = "max_level", nullable = false)
    private int maxLevel = 50;

    @Column(name = "cosmetic", nullable = false)
    private boolean cosmetic;

    @Column(name = "weight_exponent", nullable = false)
    private double weightExponent;

    @Column(name = "weight_divider", nullable = false)
    private int weightDivider;

    @Column(name = "levels", nullable = false)
    private @NotNull ConcurrentList<Level> levels = Concurrent.newList();

    public boolean notCosmetic() {
        return !this.isCosmetic();
    }

    public @NotNull ConcurrentMap<String, Double> getEffects() {
        return this.getLevels()
            .stream()
            .flatMap(level -> level.getEffects().stream())
            .collect(Concurrent.toUnmodifiableMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                Double::sum
            ));
    }

    public @NotNull ConcurrentList<Integer> getExperienceTiers() {
        return this.getLevels()
            .stream()
            .map(Level::getTotalRequiredXP)
            .collect(Concurrent.toUnmodifiableList());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Skill that = (Skill) o;

        return this.getMaxLevel() == that.getMaxLevel()
            && this.isCosmetic() == that.isCosmetic()
            && this.getWeightExponent() == that.getWeightExponent()
            && this.getWeightDivider() == that.getWeightDivider()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getLevels(), that.getLevels());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getMaxLevel(), this.isCosmetic(), this.getWeightExponent(), this.getWeightDivider(), this.getLevels());
    }

    @Getter
    @GsonType
    public static class Level {

        private int level;
        private int totalRequiredXP;
        private @NotNull String title = "";
        private @NotNull ConcurrentList<String> unlocks = Concurrent.newList();

        public @NotNull ConcurrentMap<String, Double> getEffects() {
            return MinecraftApi.getRepository(Stat.class)
                .stream()
                .map(stat -> Pair.of(
                    stat.getId(),
                    this.getUnlocks()
                        .indexedStream()
                        .collapseToSingle((line, index, size) -> {
                            String value = "0.0";

                            if (line.startsWith("+") && line.endsWith(stat.getName())) // Flat
                                value = line.split("\\s+")[0];
                            else if (line.contains("Grants +") && line.contains(stat.getName()) && index == size - 1) // Tiered
                                value = line.split("\\s+")[2];

                            value = value.replace("+", "");
                            value = value.replace("%", "");

                            if (value.contains("➜")) // Tiered
                                value = value.split("➜")[1];

                            return value;
                        })
                        .mapToDouble(Double::parseDouble)
                        .sum()
                ))
                .filter(entry -> entry.getValue() > 0.0)
                .collect(Concurrent.toUnmodifiableMap());
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Level that = (Level) o;

            return this.getLevel() == that.getLevel()
                && this.getTotalRequiredXP() == that.getTotalRequiredXP()
                && Objects.equals(this.getTitle(), that.getTitle())
                && Objects.equals(this.getUnlocks(), that.getUnlocks());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getLevel(), this.getTotalRequiredXP(), this.getTitle(), this.getUnlocks());
        }

    }

}