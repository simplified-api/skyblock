package api.simplified.skyblock.model;

import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NoArgsConstructor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.query.Indexed;
import dev.simplified.persistence.Hydration;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * A collection group - Farming, Mining, Combat, Foraging, Fishing or Rift - and, under it, every
 * resource whose gathered total unlocks tiers of recipes and rewards.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Collections">Collections</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "collections")
@Hydration(every = 10, unit = TimeUnit.MINUTES)
public class Collection implements JpaModel {

    /**
     * The group's id, one of the six skill-shaped names.
     */
    @Indexed(unique = true)
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

    /**
     * One gathered resource inside a collection group, together with the tier ladder its running
     * total climbs.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
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
        private @NotNull ConcurrentList<Tier> tiers = Concurrent.newList();

        /**
         * Amount of the resource that unlocks the final tier, read off the last rung of the ladder.
         */
        public int getMaxRequired() {
            return this.getTiers().getLast().getAmountRequired();
        }

    }

    /**
     * One rung of a collection's ladder - the amount that unlocks it and what it hands out.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
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

    }

}
