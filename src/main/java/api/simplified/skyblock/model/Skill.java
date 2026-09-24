package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.Hydration;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A skill - one of the tracks that levels as a member performs the matching activity, each level
 * granting stats and unlocking recipes.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Skills">Skills</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "skills")
@Hydration(every = 10, unit = TimeUnit.MINUTES)
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

    /**
     * One level of a skill's ladder - what it costs to reach and what reaching it awards.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
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
         * What {@link #getEffects()} resolved, held because the rows it reads cannot move underneath
         * it: a level is rebuilt from its document every time a generation is published, so an
         * instance only ever answers for the generation it was parsed into.
         */
        @Getter(AccessLevel.NONE)
        private transient @Nullable ConcurrentMap<String, Double> effects;

        /**
         * Stats the level grants, read out of its {@link #unlocks} lines.
         *
         * <p>Reading it probes the {@link Stat} repository and so needs a connected session.
         */
        public @NotNull ConcurrentMap<String, Double> getEffects() {
            ConcurrentMap<String, Double> held = this.effects;

            // Two readers racing here both resolve the same rows into equal maps, so the loser of
            // the write has nothing to lose.
            if (held == null)
                this.effects = held = Stat.Grants.of(this.getUnlocks());

            return held;
        }

    }

}