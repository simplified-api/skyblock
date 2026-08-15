package api.simplified.skyblock.model;

import api.simplified.skyblock.SkyBlockData;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A slayer track - the missions where a member kills a mob type to summon and then defeat a slayer
 * boss, levelling the track and unlocking permanent rewards.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Slayer">Slayer</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = "mobType")
@Table(name = "slayers")
public class Slayer implements JpaModel {

    /**
     * The track's id, matching the key the wire uses under a member's slayer bosses.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the track.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The one-line hint on what the track is.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * The level the track stops at, which is how far a member's progression through it can go.
     */
    @Column(name = "max_level", nullable = false)
    private int maxLevel = 9;

    /**
     * The hardest quest tier a member may start. It is a separate ceiling from {@link #maxLevel} and
     * the two are easy to confuse - a level is progression, a tier is quest difficulty.
     */
    @Column(name = "max_tier", nullable = false)
    private int maxTier = 5;

    /**
     * Id of the mob type the track's quests target, bound from the wire key {@code mobType}.
     */
    @SerializedName("mobType")
    @Column(name = "mob_type_id", nullable = false)
    private @NotNull String mobTypeId = "";

    /**
     * The modifier this track contributes to the community slayer-weight formula.
     */
    @Column(name = "weight_modifier", nullable = false)
    private double weightModifier;

    /**
     * The divider this track contributes to the community slayer-weight formula.
     */
    @Column(name = "weight_divider", nullable = false)
    private int weightDivider;

    /**
     * The level ladder, one entry per level the track offers.
     */
    @Column(name = "levels", nullable = false)
    private @NotNull ConcurrentList<Level> levels = Concurrent.newList();

    /**
     * The {@link MobType} row behind {@link #mobTypeId}, resolved on the same column.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "mob_type_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull MobType mobType;

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
     * One level of a slayer track's ladder - what it costs to reach and what reaching it awards.
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
         * The reward lines the level grants, exactly as the slayer menu prints them.
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

    }

}