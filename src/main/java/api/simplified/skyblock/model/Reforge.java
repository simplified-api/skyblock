package api.simplified.skyblock.model;

import api.simplified.skyblock.SkyBlockData;
import api.simplified.skyblock.common.Rarity;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A reforge - the modifier applied to a weapon, a piece of armour or a tool for coins, granting
 * stats that scale with the item's rarity. An item carries one reforge at a time.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Reforging">Reforging</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = "stone")
@Table(name = "reforges")
public class Reforge implements JpaModel {

    /**
     * The reforge's id, matching the value the wire stores on the reforged item.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The prefix the reforge puts in front of the item's name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Id of the reforge stone that applies the reforge, absent for the basic reforges bought at the
     * Blacksmith.
     */
    @Column(name = "stone_id")
    private @NotNull Optional<String> stoneId = Optional.empty();

    /**
     * Skill level a member needs before the reforge stone can be used.
     */
    @Column(name = "required_level", nullable = false)
    private int requiredLevel = 0;

    /**
     * Ids of the item categories that accept the reforge, bound from the wire key {@code categories}.
     */
    @SerializedName("categories")
    @Column(name = "categories", nullable = false)
    private @NotNull ConcurrentList<String> categoryIds = Concurrent.newList();

    /**
     * Ids of individual items that accept the reforge on top of those categories, bound from the wire
     * key {@code items}.
     */
    @SerializedName("items")
    @Column(name = "items", nullable = false)
    private @NotNull ConcurrentList<String> itemIds = Concurrent.newList();

    /**
     * What the reforge grants, as {@link Substitute} references whose amounts are keyed by the
     * reforged item's rarity.
     */
    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "stone_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Item stone;

    /**
     * The {@link ItemCategory} rows behind {@link #categoryIds}, filled in by the repository layer
     * rather than stored in a column.
     */
    @ForeignIds("categoryIds")
    private transient @NotNull ConcurrentList<ItemCategory> categories = Concurrent.newList();

    /**
     * The {@link Item} rows behind {@link #itemIds}, filled in by the repository layer rather than
     * stored in a column.
     */
    @ForeignIds("itemIds")
    private transient @NotNull ConcurrentList<Item> items = Concurrent.newList();

    /**
     * The {@link Item} row the reforge's stone id names, resolved on the same column and empty when
     * the reforge has no stone.
     */
    public @NotNull Optional<Item> getStone() {
        return Optional.ofNullable(this.stone);
    }

    /**
     * Whether a reforge stone id is stored. It is read off the id column rather than the resolved
     * association, so a missing item row leaves this true while {@link #getStone()} is empty.
     */
    public boolean hasStone() {
        return this.stoneId.isPresent();
    }

    /**
     * Negates {@link #hasStone()}.
     *
     * @return {@code true} when no reforge stone id is stored
     */
    public boolean notStone() {
        return !this.hasStone();
    }

    /**
     * Filters the reforge's stats to those defined at a given item rarity.
     *
     * @param rarity the rarity of the item being reforged
     * @return the substitutes carrying an amount for that rarity, empty when the reforge does not
     *     apply at it
     */
    public @NotNull ConcurrentList<Substitute> getStats(@NotNull Rarity rarity) {
        return this.getStats()
            .stream()
            .filter(stat -> stat.getValues().containsKey(rarity))
            .collect(Concurrent.toUnmodifiableList());
    }

    /**
     * A reference to a {@link Stat} by id, plus the flat amount the reforge grants at each rarity.
     *
     * <p>
     * It carries no precision, no number type and no colour, because a reforge tooltip renders the
     * amount with the stat's own formatting.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Substitute {

        /**
         * Id of the {@link Stat} the reforge grants.
         */
        private @NotNull String id = "";

        /**
         * The flat amount granted, keyed by the rarity of the item the reforge sits on.
         */
        private @NotNull ConcurrentMap<Rarity, Double> values = Concurrent.newMap();

        /**
         * The {@link Stat} this substitute names, resolved through {@link SkyBlockData#getRepository}
         * and so requiring a connected session. It is empty for a blank id rather than throwing.
         */
        public @NotNull Optional<Stat> getStat() {
            if (this.id.isEmpty())
                return Optional.empty();
            return api.simplified.skyblock.SkyBlockData.getRepository(Stat.class).findFirst(Stat::getId, this.id);
        }

    }

}