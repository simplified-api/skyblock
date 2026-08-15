package api.simplified.skyblock.model;

import api.simplified.skyblock.common.Rarity;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.ForeignIds;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import lib.minecraft.text.ChatFormat;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * One SkyBlock enchantment - the expanded form of vanilla enchanting, applied to gear from an
 * enchanted book or the enchantment table, with a level ladder and a per-level apply cost.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Enchantments">Enchantments</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "enchantments")
public class Enchantment implements JpaModel {

    /**
     * The enchantment's id.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The enchantment's display name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Tooltip template describing what the enchantment does.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * Whether the enchantment is an ordinary one or an ultimate.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private @NotNull Type type = Type.NORMAL;

    /**
     * Enchanting skill level needed before the enchantment can be applied.
     */
    @Column(name = "required_level", nullable = false)
    private int requiredLevel = 0;

    /**
     * Id of the group this enchantment competes in, so that two enchantments naming the same group
     * cannot coexist on one item. Empty when it conflicts with nothing.
     */
    @Column(name = "conflict")
    private @NotNull Optional<String> conflict = Optional.empty();

    /**
     * Ids of the item categories that accept this enchantment, bound from the {@code categories}
     * key.
     */
    @SerializedName("categories")
    @Column(name = "categories", nullable = false)
    private @NotNull ConcurrentList<String> categoryIds = Concurrent.newList();

    /**
     * Ids of specific items that accept this enchantment beyond those categories, bound from the
     * {@code items} key.
     */
    @SerializedName("items")
    @Column(name = "items", nullable = false)
    private @NotNull ConcurrentList<String> itemIds = Concurrent.newList();

    /**
     * The ladder, one entry per obtainable level.
     */
    @Column(name = "levels", nullable = false)
    private @NotNull ConcurrentList<Level> levels = Concurrent.newList();

    /**
     * Stats the enchantment grants, each a reference to a {@link Stat} by id plus the values to
     * render rather than a resolved stat.
     */
    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    /**
     * Ids of the mob types the enchantment is effective against, bound from the {@code mobTypes}
     * key.
     */
    @SerializedName("mobTypes")
    @Column(name = "mob_types", nullable = false)
    private @NotNull ConcurrentList<String> mobTypeIds = Concurrent.newList();

    /**
     * The {@link ItemCategory} rows resolved from {@link #categoryIds}, filled in by the repository
     * layer rather than bound.
     */
    @ForeignIds("categoryIds")
    private transient @NotNull ConcurrentList<ItemCategory> categories = Concurrent.newList();

    /**
     * The {@link Item} rows resolved from {@link #itemIds}, filled in by the repository layer rather
     * than bound.
     */
    @ForeignIds("itemIds")
    private transient @NotNull ConcurrentList<Item> items = Concurrent.newList();

    /**
     * The {@link MobType} rows resolved from {@link #mobTypeIds}, filled in by the repository layer
     * rather than bound.
     */
    @ForeignIds("mobTypeIds")
    private transient @NotNull ConcurrentList<MobType> mobTypes = Concurrent.newList();

    /**
     * The two kinds of enchantment, differing in how the name is drawn and how many one item may
     * carry.
     */
    @Getter
    @RequiredArgsConstructor
    public enum Type {

        /**
         * An ordinary enchantment, drawn blue.
         */
        NORMAL(ChatColor.Legacy.BLUE, false),

        /**
         * An ultimate enchantment, drawn bold light purple, of which an item may hold one.
         */
        ULTIMATE(ChatColor.Legacy.LIGHT_PURPLE, true);

        /**
         * Colour the enchantment's name is drawn in.
         */
        private final @NotNull ChatColor format;

        /**
         * Whether the name is drawn bold.
         */
        private final boolean bold;

        @Override
        public String toString() {
            return this.getFormat() + (this.isBold() ? ChatFormat.BOLD.toString() : "");
        }

    }

    /**
     * One obtainable level of an enchantment, with what it costs to apply.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Level {

        /**
         * The level number.
         */
        private int level = 0;

        /**
         * What applying this level costs, bound from the {@code cost} key.
         */
        @SerializedName("cost")
        private @NotNull Item.Cost applyCost = new Item.Cost();

        /**
         * Rarity of the enchanted book carrying this level, derived from the level number alone and
         * never checked against the book itself.
         */
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

    }

}
