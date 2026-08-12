package api.simplified.skyblock.model;

import api.simplified.skyblock.SkyBlockData;
import com.google.gson.annotations.SerializedName;
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

/**
 * A skill - one of the tracks that levels as a member performs the matching activity, each level
 * granting stats and unlocking recipes.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Skills">Skills</a>
 */
@Getter
@Entity
@Table(name = "skills")
public class Skill implements JpaModel {

    /**
     * The skill's id, matching the suffix the wire uses on a member's {@code experience_skill_*} keys.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the skill.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The one-line hint on how the skill's experience is earned.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * The level the skill stops at.
     */
    @Column(name = "max_level", nullable = false)
    private int maxLevel = 50;

    /**
     * Whether the skill is excluded from a member's skill average and skill weight.
     */
    @Column(name = "cosmetic", nullable = false)
    private boolean cosmetic;

    /**
     * The exponent this skill contributes to the community skill-weight formula.
     */
    @Column(name = "weight_exponent", nullable = false)
    private double weightExponent;

    /**
     * The divider this skill contributes to the community skill-weight formula.
     */
    @Column(name = "weight_divider", nullable = false)
    private int weightDivider;

    /**
     * The level ladder, one entry per level the skill offers.
     */
    @Column(name = "levels", nullable = false)
    private @NotNull ConcurrentList<Level> levels = Concurrent.newList();

    /**
     * Negates the {@code cosmetic} flag.
     *
     * @return {@code true} when the skill counts toward skill average and skill weight
     */
    public boolean notCosmetic() {
        return !this.isCosmetic();
    }

    /**
     * Every level's effects summed into one stat map, keyed by {@link Stat} id. It is derived rather
     * than bound, and each level reaches the {@link Stat} repository, so it needs a connected session.
     */
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

    /**
     * The ladder projected to its cumulative experience thresholds, one entry per level in level
     * order.
     */
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

    /**
     * One level of a skill's ladder - what it costs to reach and what reaching it awards.
     */
    @Getter
    @GsonType
    public static class Level {

        /**
         * The level number.
         */
        private int level;

        /**
         * The cumulative experience needed to reach the level, counted from zero rather than from the
         * level below.
         */
        @SerializedName("totalExpRequired")
        private int totalRequiredXP;

        /**
         * The title the level awards.
         */
        private @NotNull String title = "";

        /**
         * The reward lines the level grants, exactly as the skill menu prints them.
         */
        private @NotNull ConcurrentList<String> unlocks = Concurrent.newList();

        /**
         * Stats the level grants, derived by scraping each {@link #unlocks} line for a {@link Stat}
         * name and reading the number out of it - a line starting {@code +} and ending in the stat's
         * display name is a flat grant, a last line containing {@code Grants +} is a tiered one, and
         * an arrow means take the right-hand side.
         *
         * <p>
         * The match is on the wording the game prints, so an upstream rewording silently yields
         * nothing rather than failing. Reading it walks the {@link Stat} repository and so needs a
         * connected session.
         */
        public @NotNull ConcurrentMap<String, Double> getEffects() {
            return SkyBlockData.getRepository(Stat.class)
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