package api.simplified.skyblock.model;

import api.simplified.skyblock.SkinTexture;
import api.simplified.skyblock.common.GameStage;
import api.simplified.skyblock.common.Rarity;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.AllArgsConstructor;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.EqualsInclude;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.NoArgsConstructor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Optional;

/**
 * The catalogue entry for one SkyBlock item - the row behind every id the wire names in an
 * inventory, an auction, a bazaar order or a recipe.
 *
 * <p>
 * It is the largest table in this package and the join target of most of the others. The hidden
 * flags below keep the wire's {@code snake_case} spelling as their Java names so that gson binds
 * them with no annotation of their own; none has an accessor and all are read through
 * {@link #getAttributes()}.
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = {
    "categoryId", "can_place", "can_trade", "can_auction", "cannot_reforge", "can_recombobulate",
    "can_burn_in_furnace", "salvageable_from_recipe", "museum", "glowing", "unstackable",
    "dungeon_item", "rift_transferrable", "soulbound"
})
@Table(name = "items")
public class Item implements JpaModel {

    /**
     * The SkyBlock item id, and the key every other table joins on.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The underlying Minecraft material the item is built on.
     */
    @Column(name = "material", nullable = false)
    private @NotNull String material = "";

    /**
     * The name the game prints for the item.
     */
    @SerializedName("name")
    @Column(name = "display_name", nullable = false)
    private @NotNull String displayName = "";

    /**
     * The rarity band the item sits in.
     */
    @SerializedName("tier")
    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false)
    private @NotNull Rarity rarity = Rarity.COMMON;

    /**
     * The stored id of the owning {@link ItemCategory}, defaulting to the catch-all {@code OTHER}
     * category rather than to nothing.
     */
    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "OTHER";

    /**
     * Whether the item can be placed in the world.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "can_place", nullable = false)
    private boolean can_place = true;

    /**
     * Whether the item can be traded to another player.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "can_trade", nullable = false)
    private boolean can_trade = true;

    /**
     * Whether the item can be listed on the auction house.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "can_auction", nullable = false)
    private boolean can_auction = true;

    /**
     * Whether the item refuses a reforge - the one flag spelled as a refusal, which
     * {@link Attributes} inverts into a positive.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "cannot_reforge", nullable = false)
    private boolean cannot_reforge = false;

    /**
     * Whether the item's rarity can be raised by a recombobulator.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "can_recombobulate", nullable = false)
    private boolean can_recombobulate = true;

    /**
     * Whether the item can be burned as furnace fuel.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "can_burn_in_furnace", nullable = false)
    private boolean can_burn_in_furnace = false;

    /**
     * Whether salvaging the item returns the ingredients of its recipe.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "salvageable_from_recipe", nullable = false)
    private boolean salvageable_from_recipe = false;

    /**
     * Whether the item can be donated to the museum.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "museum", nullable = false)
    private boolean museum = false;

    /**
     * Whether the item is drawn with the enchantment glint.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "glowing", nullable = false)
    private boolean glowing = false;

    /**
     * Whether the item occupies a stack of one.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "unstackable", nullable = false)
    private boolean unstackable = true;

    /**
     * Whether the item belongs to the dungeon gear the Catacombs deals in.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "dungeon_item", nullable = false)
    private boolean dungeon_item = false;

    /**
     * Whether the item can be carried into the Rift.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "rift_transferrable", nullable = false)
    private boolean rift_transferrable = false;

    /**
     * How tightly the item is bound to whoever obtained it.
     */
    @Getter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "soulbound", nullable = false)
    private @NotNull Soulbound soulbound = Soulbound.NONE;

    private transient Attributes attributes;

    /**
     * The resolved {@link ItemCategory}, read from the same column {@code categoryId} is stored in.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull ItemCategory category;

    /**
     * The hidden flags folded into one value, worked out on the first call and reused thereafter.
     *
     * <p>
     * Three of the fifteen are derived rather than copied across: the refusal flag is inverted into
     * a positive {@code reforgeable}, {@code obtainable} reads the rarity being anything but
     * {@link Rarity#ADMIN}, and {@code npcSellable} reads an NPC sell price above zero. A flag the
     * data omits keeps the permissive default, so a bare row answers true to most of them.
     */
    @EqualsInclude
    public @NotNull Attributes getAttributes() {
        if (this.attributes == null) {
            this.attributes = new Attributes(
                this.npcSellPrice > 0,
                this.can_place,
                this.can_trade,
                this.can_auction,
                !this.cannot_reforge,
                this.can_recombobulate,
                this.can_burn_in_furnace,
                this.salvageable_from_recipe,
                this.museum,
                this.glowing,
                this.unstackable,
                this.dungeon_item,
                this.rift_transferrable,
                this.rarity != Rarity.ADMIN,
                this.soulbound
            );
        }

        return this.attributes;
    }

    /**
     * The material's damage value, present only where the material needs one to be identified.
     */
    @Column(name = "durability")
    private @NotNull Optional<Integer> durability = Optional.empty();

    /**
     * The body of the item's tooltip.
     */
    @Column(name = "description")
    private @NotNull Optional<String> description = Optional.empty();

    /**
     * The dye colour leather armour is drawn in.
     */
    @Column(name = "color")
    private @NotNull Optional<Color> color = Optional.empty();

    /**
     * Where the item came from, for the items the game tags with a source.
     */
    @Column(name = "origin")
    private @NotNull Optional<String> origin = Optional.empty();

    /**
     * The player head texture worn by head-shaped items.
     */
    @Column(name = "skin")
    private @NotNull Optional<SkinTexture> skin = Optional.empty();

    /**
     * The furniture grouping the item belongs to, carried through exactly as the data spells it.
     */
    @Column(name = "furniture")
    private @NotNull Optional<String> furniture = Optional.empty();

    /**
     * The crystal grouping the item belongs to, carried through exactly as the data spells it.
     */
    @Column(name = "crystal")
    private @NotNull Optional<String> crystal = Optional.empty();

    /**
     * The museum's view of the item, held in memory only and never written to a column.
     */
    @SerializedName("museum_data")
    @Transient
    private @NotNull Optional<MuseumData> museumData = Optional.empty();

    /**
     * The sword grouping the item belongs to, carried through exactly as the data spells it.
     */
    @SerializedName("sword_type")
    @Column(name = "sword_type")
    private @NotNull Optional<String> swordType = Optional.empty();

    /**
     * The private island generator the item places.
     */
    @SerializedName("private_island")
    @Column(name = "mini_island_generator")
    private @NotNull Optional<String> miniIslandGenerator = Optional.empty();

    /**
     * The coins an NPC pays for one of the item.
     */
    @SerializedName("npc_sell_price")
    @Column(name = "npc_sell_price", nullable = false)
    private double npcSellPrice;

    /**
     * How the damage of the item's ability scales.
     */
    @SerializedName("ability_damage_scaling")
    @Column(name = "ability_damage_scaling", nullable = false)
    private double abilityDamageScaling;

    /**
     * The item's gear score.
     */
    @SerializedName("gear_score")
    @Column(name = "gear_score", nullable = false)
    private int gearScore;

    /**
     * What converting the item into a dungeon item costs.
     */
    @SerializedName("dungeon_item_conversion_cost")
    @Column(name = "dungeonization_cost", nullable = false)
    private @NotNull ConcurrentMap<String, Object> dungeonizationCost = Concurrent.newMap();

    /**
     * The Catacombs progress a member must have before the item can be used.
     */
    @SerializedName("catacombs_requirements")
    @Column(name = "catacombs_requirements", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> catacombsRequirements = Concurrent.newList();

    /**
     * Whether carrying the item into the Rift burns away its motes value.
     */
    @SerializedName("lose_motes_value_on_transfer")
    @Column(name = "motes_value_lost_on_transfer", nullable = false)
    private boolean motesValueLostOnTransfer;

    /**
     * The motes an NPC inside the Rift pays for one of the item.
     */
    @SerializedName("motes_sell_price")
    @Column(name = "motes_sell_price", nullable = false)
    private double motesSellPrice;

    /**
     * The minion the item places, present only on the items that are minions.
     */
    @Column(name = "generator")
    private @NotNull Optional<String> generator = Optional.empty();

    /**
     * The tier of the minion the item places.
     */
    @SerializedName("generator_tier")
    @Column(name = "generator_tier", nullable = false)
    private int generatorTier;

    /**
     * The enchantments the item ships with, keyed by {@link Enchantment} id.
     */
    @Column(name = "enchantments", nullable = false)
    private @NotNull ConcurrentMap<String, Double> enchantments = Concurrent.newMap();

    /**
     * The gemstone slots cut into the item.
     */
    @SerializedName("gemstone_slots")
    @Column(name = "gemstone_slots", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> gemstoneSlots = Concurrent.newList();

    /**
     * A free-form bag of settings that apply to this item alone.
     */
    @SerializedName("item_specific")
    @Column(name = "item_specific", nullable = false)
    private @NotNull ConcurrentMap<String, Object> itemSpecific = Concurrent.newMap();

    /**
     * The prestige chain the item belongs to.
     */
    @Column(name = "prestige", nullable = false)
    private @NotNull ConcurrentMap<String, Object> prestige = Concurrent.newMap();

    /**
     * What a member must have before the item can be used.
     */
    @Column(name = "requirements", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> requirements = Concurrent.newList();

    /**
     * What salvaging the item returns.
     */
    @Column(name = "salvages", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> salvages = Concurrent.newList();

    /**
     * The item's own stats, keyed by {@link Stat} id.
     */
    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentMap<String, Double> stats = Concurrent.newMap();

    /**
     * The stats that change per star or tier, keyed by {@link Stat} id with one amount per step.
     */
    @SerializedName("tiered_stats")
    @Column(name = "tiered_stats", nullable = false)
    private @NotNull ConcurrentMap<String, List<Double>> tieredStats = Concurrent.newMap();

    /**
     * What each upgrade step costs, one entry per step.
     */
    @SerializedName("upgrade_costs")
    @Column(name = "upgrade_costs", nullable = false)
    private @NotNull ConcurrentList<ConcurrentList<ConcurrentMap<String, Object>>> upgradeCosts = Concurrent.newList();

    /**
     * The Minecraft material, suffixed with {@code :durability} where the item carries one.
     */
    public @NotNull String getMinecraftId() {
        return this.getDurability()
            .map(value -> String.format("%s:%s", this.getMaterial(), value))
            .orElse(this.getMaterial());
    }

    /**
     * Whether the item can be converted into a dungeon item, which is a conversion cost being
     * present.
     */
    public boolean isDungeonizable() {
        return this.getDungeonizationCost().notEmpty();
    }

    /**
     * Whether the item places a minion, which is a generator being present.
     */
    public boolean isMinion() {
        return this.getGenerator().isPresent();
    }

    /**
     * What an item permits, gathered into one value with a negation helper beside each answer.
     *
     * <p>
     * Most of these copy a hidden flag across unchanged; {@code reforgeable}, {@code obtainable} and
     * {@code npcSellable} are worked out instead, and are the reason the value is built rather than
     * bound.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attributes {

        /**
         * Whether an NPC pays anything at all for the item.
         */
        private boolean npcSellable;

        /**
         * Whether the item can be placed in the world.
         */
        private boolean placeable;

        /**
         * Whether the item can be traded to another player.
         */
        private boolean tradeable;

        /**
         * Whether the item can be listed on the auction house.
         */
        private boolean auctionable;

        /**
         * Whether the item accepts a reforge.
         */
        private boolean reforgeable;

        /**
         * Whether the item's rarity can be raised by a recombobulator.
         */
        private boolean recombobulatable;

        /**
         * Whether the item can be burned as furnace fuel.
         */
        private boolean burnableInFurnace;

        /**
         * Whether salvaging the item returns the ingredients of its recipe.
         */
        private boolean salvageableFromRecipe;

        /**
         * Whether the item can be donated to the museum.
         */
        private boolean museumable;

        /**
         * Whether the item is drawn with the enchantment glint.
         */
        private boolean glowing;

        /**
         * Whether the item occupies a stack of one.
         */
        private boolean unstackable;

        /**
         * Whether the item belongs to the dungeon gear the Catacombs deals in.
         */
        private boolean dungeonItem;

        /**
         * Whether the item can be carried into the Rift.
         */
        private boolean riftTransferrable;

        /**
         * Whether a member can come by the item at all, false only for the admin-only rarity.
         */
        private boolean obtainable;

        /**
         * How tightly the item is bound to whoever obtained it.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Soulbound soulbound;

        public boolean notSellable() { return !this.isNpcSellable(); }
        public boolean notPlaceable() { return !this.isPlaceable(); }
        public boolean notTradable() { return !this.isTradeable(); }
        public boolean notAuctionable() { return !this.isAuctionable(); }
        public boolean notReforgeable() { return !this.isReforgeable(); }
        public boolean notRecombobulatable() { return !this.isRecombobulatable(); }
        public boolean notBurnableInFurnace() { return !this.isBurnableInFurnace(); }
        public boolean notSalvageableFromRecipe() { return !this.isSalvageableFromRecipe(); }
        public boolean notMuseumable() { return !this.isMuseumable(); }
        public boolean notGlowing() { return !this.isGlowing(); }
        public boolean notStackable() { return !this.isUnstackable(); }
        public boolean notDungeonItem() { return !this.isDungeonItem(); }
        public boolean notRiftTransferrable() { return !this.isRiftTransferrable(); }
        public boolean notObtainable() { return !this.isObtainable(); }

    }

    /**
     * The museum's view of an item - which tab it is filed under, what donating it is worth, and
     * which set it belongs to.
     */
    @Getter
    @GsonType
    public static class MuseumData {

        /**
         * The SkyBlock experience for donating the item on its own.
         */
        @SerializedName("donation_xp")
        private int donationXP;

        /**
         * The museum tab the item is filed under.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull MuseumData.Type type = MuseumData.Type.UNKNOWN;

        /**
         * The item this one is a variant of.
         */
        private @NotNull ConcurrentMap<String, String> parent = Concurrent.newMap();

        /**
         * The SkyBlock experience for donating the whole armour set, keyed by set id and read
         * through {@link #getActualXP()} and {@link #getArmorSet()}.
         */
        @SerializedName("armor_set_donation_xp")
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentMap<String, Integer> armorSetDonationXP = Concurrent.newMap();

        /**
         * How far into the game the museum files the donation.
         */
        @SerializedName("game_stage")
        @Enumerated(EnumType.STRING)
        private @NotNull GameStage gameStage = GameStage.UNKNOWN;

        /**
         * The experience a donation is actually worth, preferring the armour set figure and falling
         * back to the item's own.
         */
        public int getActualXP() {
            return this.armorSetDonationXP.stream()
                .findFirst()
                .map(ConcurrentMap.Entry::getValue)
                .orElse(this.getDonationXP());
        }

        /**
         * The armour set the item donates as part of, empty when it donates on its own.
         */
        public @NotNull Optional<String> getArmorSet() {
            return this.armorSetDonationXP.stream()
                .findFirst()
                .map(ConcurrentMap.Entry::getKey);
        }

        /**
         * The tabs the museum files a donation under.
         */
        public enum Type {

            /**
             * No recognised tab, the default and what an unrecognised value binds to.
             */
            UNKNOWN,

            /**
             * The armour sets tab.
             */
            ARMOR_SETS,

            /**
             * The weapons tab.
             */
            WEAPONS,

            /**
             * The rarities tab.
             */
            RARITIES,

            /**
             * The special items tab.
             */
            SPECIAL

        }

    }

    /**
     * How tightly an item is bound to whoever obtained it.
     *
     * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Soulbound">Soulbound</a>
     */
    public enum Soulbound {

        /**
         * Not bound at all, so the item moves freely between players.
         */
        NONE,

        /**
         * Bound to the member who obtained it.
         */
        SOLO,

        /**
         * Bound to the profile, so co-op members may pass it between them.
         */
        COOP

    }

    /**
     * The coarse grouping an {@link ItemCategory} rolls up to.
     */
    public enum Type {

        /**
         * Armour worn in one of the four armour slots.
         */
        ARMOR,

        /**
         * Something kept or donated rather than used.
         */
        COLLECTIBLE,

        /**
         * Something spent on use.
         */
        CONSUMABLE,

        /**
         * Gear worn in one of the equipment slots beside the armour.
         */
        EQUIPMENT,

        /**
         * Anything with no coarser grouping of its own, and the default.
         */
        OTHER,

        /**
         * Something used to gather a resource.
         */
        TOOL,

        /**
         * Something used to deal damage.
         */
        WEAPON

    }

    /**
     * A price, expressed as skill experience, currency amounts and items to hand over.
     *
     * <p>
     * It is the shape reused wherever this package needs to say what something costs - a brew, an
     * enchantment level, a minion tier and an essence shop unlock all hold one.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Cost {

        /**
         * The skill experience required.
         */
        private int experience = 0;

        /**
         * The amounts required, keyed by currency.
         */
        private @NotNull ConcurrentMap<Currency, Double> currencies = Concurrent.newMap();

        /**
         * The quantities required, keyed by {@link Item} id.
         */
        private @NotNull ConcurrentMap<String, Double> items = Concurrent.newMap();

        /**
         * The currencies a price can be denominated in.
         *
         * <p>
         * Every constant names its own spelling because the data writes them lowercase or in camel
         * case rather than as the constant.
         */
        public enum Currency {

            /**
             * Coins, the general-purpose currency.
             */
            @SerializedName("coins")
            COINS,

            /**
             * Essence, spent at the Essence Shops.
             */
            @SerializedName("essence")
            ESSENCE,

            /**
             * Motes, the currency of the Rift.
             */
            @SerializedName("motes")
            MOTES,

            /**
             * Stars, spent on upgrading gear a star at a time.
             */
            @SerializedName("stars")
            STARS,

            /**
             * North stars, the currency of Jerry's Workshop.
             */
            @SerializedName("northStars")
            NORTH_STARS,

            /**
             * Pelts, the currency the Trapper pays in.
             */
            @SerializedName("pelts")
            PELTS

        }

    }

}