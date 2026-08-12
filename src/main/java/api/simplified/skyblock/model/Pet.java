package api.simplified.skyblock.model;

import api.simplified.skyblock.SkyBlockData;
import api.simplified.skyblock.common.Rarity;
import com.google.gson.annotations.SerializedName;
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
import lib.minecraft.text.ChatColor;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A pet type - a companion granting stats and abilities that scale with both its level and its
 * rarity, tied to the one skill whose experience levels it.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Pets">Pets</a>
 */
@Getter
@Entity
@Table(name = "pets")
public class Pet implements JpaModel {

    /**
     * The pet's id, matching the pet type the wire names.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The name the pet is shown under.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The weakest rarity the pet exists at, so anything below it is not obtainable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "lowest_rarity", nullable = false)
    private @NotNull Rarity lowestRarity = Rarity.COMMON;

    /**
     * The stored id of the skill whose experience levels the pet.
     */
    @SerializedName("skill")
    @Column(name = "skill_id", nullable = false)
    private @NotNull String skillId = "";

    /**
     * Which kind of companion the pet is.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private @NotNull Type type = Type.PET;

    /**
     * The level ceiling, 100 for most pets and 200 for the golden dragon.
     */
    @Column(name = "max_level", nullable = false)
    private int maxLevel = 100;

    /**
     * Whether the pet's effect applies while it is stowed rather than only while summoned.
     */
    @Column(name = "passive", nullable = false)
    private boolean passive = false;

    /**
     * The flat stats the pet grants, as references to a {@link Stat} by id.
     */
    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

    /**
     * The pet's named abilities.
     */
    @Column(name = "abilities", nullable = false)
    private @NotNull ConcurrentList<Ability> abilities = Concurrent.newList();

    /**
     * The resolved {@link Skill}, read from the same column {@code skillId} is stored in.
     */
    @ManyToOne
    @JoinColumn(name = "skill_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Skill skill;

    /**
     * Narrows the pet's stats to those defined at a given rarity.
     *
     * @param rarity the rarity to read the grant at
     * @return the stat references carrying a value for that rarity
     */
    public @NotNull ConcurrentList<Substitute> getStats(@NotNull Rarity rarity) {
        return this.getStats()
            .stream()
            .filter(stat -> stat.getValues().containsKey(rarity))
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * Narrows the pet's abilities to those granting something at a given rarity.
     *
     * @param rarity the rarity to read the grant at
     * @return the abilities carrying at least one stat defined for that rarity
     */
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

    /**
     * The kinds of companion a pet row can describe.
     */
    public enum Type {

        /**
         * An ordinary pet that follows the member around, and the default.
         */
        PET,

        /**
         * A companion the member rides.
         */
        MOUNT,

        /**
         * A companion that changes the player's own form.
         */
        MORPH,

        /**
         * The joke companion, kept as a category of its own.
         */
        GABAGOOL;

        /**
         * The constant's name in title case, with underscores rendered as spaces.
         */
        public @NotNull String getName() {
            return StringUtil.capitalizeFully(this.name().replace("_", " "));
        }

    }

    /**
     * One named ability a pet grants alongside its flat stats.
     */
    @Getter
    @GsonType
    public static class Ability {

        /**
         * The name the ability is shown under.
         */
        private @NotNull String name = "";

        /**
         * The template line describing the ability, carrying the placeholders its stats fill in.
         */
        private @NotNull String description = "";

        /**
         * Whether the ability grants a flat amount rather than one that scales with the pet's level.
         */
        private boolean flatStat = false;
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

        /**
         * The stats the ability grants, across every rarity it is defined at.
         */
        public @NotNull ConcurrentList<Substitute> getStats() {
            return this.stats;
        }

        /**
         * Narrows the ability's stats to those defined at a given rarity.
         *
         * @param rarity the rarity to read the grant at
         * @return the stat references carrying a value for that rarity
         */
        public @NotNull ConcurrentList<Substitute> getStats(@NotNull Rarity rarity) {
            return this.getStats()
                .stream()
                .filter(stat -> stat.getValues().containsKey(rarity))
                .collect(Concurrent.toUnmodifiableList());
        }

        /**
         * The description with its placeholders substituted - the raw description as it stands,
         * because the substitution is not written yet.
         */
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

    /**
     * A reference to a {@link Stat} by id, together with the values a pet renders for it, keyed by
     * rarity.
     *
     * <p>
     * The amount at a level is the base plus the level times the scalar, which is what
     * {@link Stat.Type#format} works out from one {@link Value}. A rarity absent from the map means
     * the pet grants that stat nothing at that rarity rather than zero of it.
     */
    @Getter
    @GsonType
    public static class Substitute {

        /**
         * The id of the {@link Stat} being granted.
         */
        private @NotNull String id = "";

        /**
         * Decimal places to render the amount to.
         */
        private int precision = 0;

        /**
         * How to decorate the number, which is the prefix and suffix it is drawn with.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Stat.Type type = Stat.Type.NONE;

        /**
         * The colour to draw the amount in.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GREEN;
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentMap<Rarity, Value> values = Concurrent.newMap();

        /**
         * The grant keyed by rarity, each entry the pair the level scaling is computed from.
         */
        public @NotNull ConcurrentMap<Rarity, Value> getValues() {
            return this.values;
        }

        /**
         * The {@link Stat} this substitute names, looked up in the {@link SkyBlockData} repository
         * and so needing a connected session; empty when no id is set.
         */
        public @NotNull java.util.Optional<Stat> getStat() {
            if (this.id.isEmpty())
                return java.util.Optional.empty();
            return api.simplified.skyblock.SkyBlockData.getRepository(Stat.class).findFirst(Stat::getId, this.id);
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

        /**
         * The two halves of a grant that scales with a pet's level.
         */
        @Getter
        @GsonType
        public static class Value {

            /**
             * The amount granted at level zero.
             */
            private double base = 0.0;

            /**
             * The amount added for each level the pet has gained.
             */
            private double scalar = 0.0;

        }

    }

}