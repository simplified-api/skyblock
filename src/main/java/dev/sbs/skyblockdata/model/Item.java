package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.api.persistence.type.GsonType;
import dev.sbs.minecraftapi.client.mojang.response.MojangProperty;
import dev.sbs.minecraftapi.skyblock.common.GameStage;
import dev.sbs.minecraftapi.skyblock.common.Rarity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "items")
public class Item implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "material", nullable = false)
    private @NotNull String material = "";

    @SerializedName("name")
    @Column(name = "display_name", nullable = false)
    private @NotNull String displayName = "";

    @SerializedName("tier")
    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false)
    private @NotNull Rarity rarity = Rarity.COMMON;

    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "OTHER";

    @Getter(AccessLevel.NONE)
    @Column(name = "can_place", nullable = false)
    private boolean can_place = true;

    @Getter(AccessLevel.NONE)
    @Column(name = "can_trade", nullable = false)
    private boolean can_trade = true;

    @Getter(AccessLevel.NONE)
    @Column(name = "can_auction", nullable = false)
    private boolean can_auction = true;

    @Getter(AccessLevel.NONE)
    @Column(name = "cannot_reforge", nullable = false)
    private boolean cannot_reforge = false;

    @Getter(AccessLevel.NONE)
    @Column(name = "can_recombobulate", nullable = false)
    private boolean can_recombobulate = true;

    @Getter(AccessLevel.NONE)
    @Column(name = "can_burn_in_furnace", nullable = false)
    private boolean can_burn_in_furnace = false;

    @Getter(AccessLevel.NONE)
    @Column(name = "salvageable_from_recipe", nullable = false)
    private boolean salvageable_from_recipe = false;

    @Getter(AccessLevel.NONE)
    @Column(name = "museum", nullable = false)
    private boolean museum = false;

    @Getter(AccessLevel.NONE)
    @Column(name = "glowing", nullable = false)
    private boolean glowing = false;

    @Getter(AccessLevel.NONE)
    @Column(name = "unstackable", nullable = false)
    private boolean unstackable = true;

    @Getter(AccessLevel.NONE)
    @Column(name = "dungeon_item", nullable = false)
    private boolean dungeon_item = false;

    @Getter(AccessLevel.NONE)
    @Column(name = "rift_transferrable", nullable = false)
    private boolean rift_transferrable = false;

    @Getter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "soulbound", nullable = false)
    private @NotNull Soulbound soulbound = Soulbound.NONE;

    private transient Attributes attributes;

    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull ItemCategory category;

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

    @Column(name = "durability")
    private @NotNull Optional<Integer> durability = Optional.empty();

    @Column(name = "description")
    private @NotNull Optional<String> description = Optional.empty();

    @Column(name = "color")
    private @NotNull Optional<Color> color = Optional.empty();

    @Column(name = "origin")
    private @NotNull Optional<String> origin = Optional.empty();

    @Column(name = "skin")
    private @NotNull Optional<MojangProperty> skin = Optional.empty();

    @Column(name = "furniture")
    private @NotNull Optional<String> furniture = Optional.empty();

    @Column(name = "crystal")
    private @NotNull Optional<String> crystal = Optional.empty();

    @SerializedName("museum_data")
    @Transient
    private @NotNull Optional<MuseumData> museumData = Optional.empty();

    @SerializedName("sword_type")
    @Column(name = "sword_type")
    private @NotNull Optional<String> swordType = Optional.empty();

    @SerializedName("private_island")
    @Column(name = "mini_island_generator")
    private @NotNull Optional<String> miniIslandGenerator = Optional.empty();

    @SerializedName("npc_sell_price")
    @Column(name = "npc_sell_price", nullable = false)
    private double npcSellPrice;

    @SerializedName("ability_damage_scaling")
    @Column(name = "ability_damage_scaling", nullable = false)
    private double abilityDamageScaling;

    @SerializedName("gear_score")
    @Column(name = "gear_score", nullable = false)
    private int gearScore;

    @SerializedName("dungeon_item_conversion_cost")
    @Column(name = "dungeonization_cost", nullable = false)
    private @NotNull ConcurrentMap<String, Object> dungeonizationCost = Concurrent.newMap();

    @SerializedName("catacombs_requirements")
    @Column(name = "catacombs_requirements", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> catacombsRequirements = Concurrent.newList();

    @SerializedName("lose_motes_value_on_transfer")
    @Column(name = "motes_value_lost_on_transfer", nullable = false)
    private boolean motesValueLostOnTransfer;

    @SerializedName("motes_sell_price")
    @Column(name = "motes_sell_price", nullable = false)
    private double motesSellPrice;

    @Column(name = "generator")
    private @NotNull Optional<String> generator = Optional.empty();

    @SerializedName("generator_tier")
    @Column(name = "generator_tier", nullable = false)
    private int generatorTier;

    @Column(name = "enchantments", nullable = false)
    private @NotNull ConcurrentMap<String, Double> enchantments = Concurrent.newMap();

    @SerializedName("gemstone_slots")
    @Column(name = "gemstone_slots", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> gemstoneSlots = Concurrent.newList();

    @SerializedName("item_specific")
    @Column(name = "item_specific", nullable = false)
    private @NotNull ConcurrentMap<String, Object> itemSpecific = Concurrent.newMap();

    @Column(name = "prestige", nullable = false)
    private @NotNull ConcurrentMap<String, Object> prestige = Concurrent.newMap();

    @Column(name = "requirements", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> requirements = Concurrent.newList();

    @Column(name = "salvages", nullable = false)
    private @NotNull ConcurrentList<ConcurrentMap<String, Object>> salvages = Concurrent.newList();

    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentMap<String, Double> stats = Concurrent.newMap();

    @SerializedName("tiered_stats")
    @Column(name = "tiered_stats", nullable = false)
    private @NotNull ConcurrentMap<String, List<Double>> tieredStats = Concurrent.newMap();

    @SerializedName("upgrade_costs")
    @Column(name = "upgrade_costs", nullable = false)
    private @NotNull ConcurrentList<ConcurrentList<ConcurrentMap<String, Object>>> upgradeCosts = Concurrent.newList();

    public @NotNull String getMinecraftId() {
        return this.getDurability()
            .map(value -> String.format("%s:%s", this.getMaterial(), value))
            .orElse(this.getMaterial());
    }

    public boolean isDungeonizable() {
        return this.getDungeonizationCost().notEmpty();
    }

    public boolean isMinion() {
        return this.getGenerator().isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Item item = (Item) o;

        return this.getNpcSellPrice() == item.getNpcSellPrice()
            && this.getAbilityDamageScaling() == item.getAbilityDamageScaling()
            && Objects.equals(this.getAttributes(), item.getAttributes())
            && this.getGearScore() == item.getGearScore()
            && this.isMotesValueLostOnTransfer() == item.isMotesValueLostOnTransfer()
            && this.getMotesSellPrice() == item.getMotesSellPrice()
            && this.getGeneratorTier() == item.getGeneratorTier()
            && Objects.equals(this.getMaterial(), item.getMaterial())
            && Objects.equals(this.getId(), item.getId())
            && Objects.equals(this.getDisplayName(), item.getDisplayName())
            && Objects.equals(this.getRarity(), item.getRarity())
            && Objects.equals(this.getDurability(), item.getDurability())
            && Objects.equals(this.getCategory(), item.getCategory())
            && Objects.equals(this.getDescription(), item.getDescription())
            && Objects.equals(this.getColor(), item.getColor())
            && Objects.equals(this.getOrigin(), item.getOrigin())
            && Objects.equals(this.getSkin(), item.getSkin())
            && Objects.equals(this.getFurniture(), item.getFurniture())
            && Objects.equals(this.getCrystal(), item.getCrystal())
            && Objects.equals(this.getMuseumData(), item.getMuseumData())
            && Objects.equals(this.getSwordType(), item.getSwordType())
            && Objects.equals(this.getMiniIslandGenerator(), item.getMiniIslandGenerator())
            && Objects.equals(this.getDungeonizationCost(), item.getDungeonizationCost())
            && Objects.equals(this.getCatacombsRequirements(), item.getCatacombsRequirements())
            && Objects.equals(this.getGenerator(), item.getGenerator())
            && Objects.equals(this.getEnchantments(), item.getEnchantments())
            && Objects.equals(this.getGemstoneSlots(), item.getGemstoneSlots())
            && Objects.equals(this.getItemSpecific(), item.getItemSpecific())
            && Objects.equals(this.getPrestige(), item.getPrestige())
            && Objects.equals(this.getRequirements(), item.getRequirements())
            && Objects.equals(this.getSalvages(), item.getSalvages())
            && Objects.equals(this.getStats(), item.getStats())
            && Objects.equals(this.getTieredStats(), item.getTieredStats())
            && Objects.equals(this.getUpgradeCosts(), item.getUpgradeCosts());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getMaterial(), this.getId(), this.getDisplayName(), this.getRarity(), this.getDurability(), this.getCategory(), this.getDescription(), this.getColor(), this.getOrigin(), this.getSkin(), this.getFurniture(), this.getCrystal(), this.getMuseumData(), this.getSwordType(), this.getMiniIslandGenerator(), this.getNpcSellPrice(), this.getAbilityDamageScaling(), this.getAttributes(), this.getGearScore(), this.getDungeonizationCost(), this.getCatacombsRequirements(), this.isMotesValueLostOnTransfer(), this.getMotesSellPrice(), this.getGenerator(), this.getGeneratorTier(), this.getEnchantments(), this.getGemstoneSlots(), this.getItemSpecific(), this.getPrestige(), this.getRequirements(), this.getSalvages(), this.getStats(), this.getTieredStats(), this.getUpgradeCosts());
    }

    @Getter
    @GsonType
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Attributes {

        private boolean npcSellable;
        private boolean placeable;
        private boolean tradeable;
        private boolean auctionable;
        private boolean reforgeable;
        private boolean recombobulatable;
        private boolean burnableInFurnace;
        private boolean salvageableFromRecipe;
        private boolean museumable;
        private boolean glowing;
        private boolean unstackable;
        private boolean dungeonItem;
        private boolean riftTransferrable;
        private boolean obtainable;
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

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Attributes that = (Attributes) o;

            return this.isNpcSellable() == that.isNpcSellable()
                && this.isPlaceable() == that.isPlaceable()
                && this.isTradeable() == that.isTradeable()
                && this.isAuctionable() == that.isAuctionable()
                && this.isReforgeable() == that.isReforgeable()
                && this.isRecombobulatable() == that.isRecombobulatable()
                && this.isBurnableInFurnace() == that.isBurnableInFurnace()
                && this.isSalvageableFromRecipe() == that.isSalvageableFromRecipe()
                && this.isMuseumable() == that.isMuseumable()
                && this.isGlowing() == that.isGlowing()
                && this.isUnstackable() == that.isUnstackable()
                && this.isDungeonItem() == that.isDungeonItem()
                && this.isRiftTransferrable() == that.isRiftTransferrable()
                && this.isObtainable() == that.isObtainable()
                && Objects.equals(this.getSoulbound(), that.getSoulbound());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.isNpcSellable(), this.isPlaceable(), this.isTradeable(), this.isAuctionable(), this.isReforgeable(), this.isRecombobulatable(), this.isBurnableInFurnace(), this.isSalvageableFromRecipe(), this.isMuseumable(), this.isGlowing(), this.isUnstackable(), this.isDungeonItem(), this.isRiftTransferrable(), this.isObtainable(), this.getSoulbound());
        }

    }

    @Getter
    @GsonType
    @NoArgsConstructor(access = AccessLevel.NONE)
    public static class MuseumData {

        @SerializedName("donation_xp")
        private int donationXP;
        @Enumerated(EnumType.STRING)
        private @NotNull MuseumData.Type type = MuseumData.Type.UNKNOWN;
        private @NotNull ConcurrentMap<String, String> parent = Concurrent.newMap();
        @SerializedName("armor_set_donation_xp")
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentMap<String, Integer> armorSetDonationXP = Concurrent.newMap();
        @SerializedName("game_stage")
        @Enumerated(EnumType.STRING)
        private @NotNull GameStage gameStage = GameStage.UNKNOWN;

        public int getActualXP() {
            return this.armorSetDonationXP.stream()
                .findFirst()
                .map(ConcurrentMap.Entry::getValue)
                .orElse(this.getDonationXP());
        }

        public @NotNull Optional<String> getArmorSet() {
            return this.armorSetDonationXP.stream()
                .findFirst()
                .map(ConcurrentMap.Entry::getKey);
        }

        public enum Type {

            UNKNOWN,
            ARMOR_SETS,
            WEAPONS,
            RARITIES,
            SPECIAL

        }

    }

    public enum Soulbound {

        NONE,
        SOLO,
        COOP

    }

    public enum Type {

        ARMOR,
        COLLECTIBLE,
        CONSUMABLE,
        EQUIPMENT,
        OTHER,
        TOOL,
        WEAPON

    }

    @Getter
    @GsonType
    public static class Cost {

        private int experience = 0;
        private @NotNull ConcurrentMap<Currency, Double> currencies = Concurrent.newMap();
        private @NotNull ConcurrentMap<String, Double> items = Concurrent.newMap();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Cost that = (Cost) o;

            return Objects.equals(this.getCurrencies(), that.getCurrencies())
                && this.getExperience() == that.getExperience()
                && Objects.equals(this.getItems(), that.getItems());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getCurrencies(), this.getExperience(), this.getItems());
        }

        public enum Currency {

            @SerializedName("coins")
            COINS,
            @SerializedName("essence")
            ESSENCE,
            @SerializedName("motes")
            MOTES,
            @SerializedName("stars")
            STARS,
            @SerializedName("northStars")
            NORTH_STARS,
            @SerializedName("pelts")
            PELTS

        }

    }

}