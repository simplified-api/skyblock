package api.simplified.skyblock.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A collection group - Farming, Mining, Combat, Foraging, Fishing or Rift - and, under it, every
 * resource whose gathered total unlocks tiers of recipes and rewards.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Collections">Collections</a>
 */
@Getter
@Entity
@Table(name = "collections")
public class Collection implements JpaModel {

    /**
     * The group's id, one of the six skill-shaped names.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The group's display name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Every collection in the group, keyed by collection id. That id can carry a colon, so
     * {@code INK_SACK:3} and {@code INK_SACK} are different keys and never fold together.
     */
    @Column(name = "items", nullable = false)
    private @NotNull ConcurrentMap<String, Item> items = Concurrent.newMap();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Collection that = (Collection) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getItems(), that.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getItems());
    }

    /**
     * One gathered resource inside a collection group, together with the tier ladder its running
     * total climbs.
     */
    @Getter
    @GsonType
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    public static class Item {

        /**
         * The resource's display name.
         */
        private @NotNull String name;

        /**
         * How many tiers the collection offers.
         */
        private int maxTiers;

        /**
         * The tier ladder itself, in ascending order.
         */
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentList<Tier> tiers = Concurrent.newList();

        /**
         * The tier ladder itself, in ascending order.
         */
        public @NotNull ConcurrentList<Tier> getTiers() {
            return this.tiers;
        }

        /**
         * Amount of the resource that unlocks the final tier, read off the last rung of the ladder.
         */
        public int getMaxRequired() {
            return this.getTiers().get(this.getTiers().size() - 1).getAmountRequired();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Item that = (Item) o;

            return this.getMaxTiers() == that.getMaxTiers()
                && Objects.equals(this.getName(), that.getName())
                && Objects.equals(this.getTiers(), that.getTiers());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getName(), this.getMaxTiers(), this.getTiers());
        }

    }

    /**
     * One rung of a collection's ladder - the amount that unlocks it and what it hands out.
     */
    @Getter
    @GsonType
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Tier {

        /**
         * The tier number, counting from one.
         */
        private int tier;

        /**
         * Cumulative amount of the resource that unlocks this tier.
         */
        private int amountRequired;

        /**
         * Reward lines this tier hands out - recipes and SkyBlock experience, exactly as the menu
         * prints them.
         */
        private @NotNull ConcurrentList<String> unlocks = Concurrent.newList();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Tier that = (Tier) o;

            return this.getTier() == that.getTier()
                && this.getAmountRequired() == that.getAmountRequired()
                && Objects.equals(this.getUnlocks(), that.getUnlocks());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getTier(), this.getAmountRequired(), this.getUnlocks());
        }

    }

}
