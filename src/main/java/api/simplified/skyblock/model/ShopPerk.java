package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.ForeignIds;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An Essence Shop perk - a permanent upgrade bought with essence at the shop of one region, unlocked
 * tier by tier with each tier costing more than the last.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Essence_Shops">Essence Shops</a>
 */
@Getter
@Entity
@Table(name = "shop_perks")
public class ShopPerk implements JpaModel {

    /**
     * The perk's id, matching the key the wire uses under a member's purchased perks.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the perk.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Template text for the perk's tooltip, carrying the {@code %{ZONE:THE_CATACOMBS}},
     * {@code %{KEYWORD:MAGICAL_POWER}} and {@code %{VALUE:SLAYER_XP}} placeholders that a
     * {@link Zone}, a {@link Keyword} and the substitute values fill in.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * Ids of the regions whose shops sell the perk, bound from the wire key {@code regions}.
     */
    @SerializedName("regions")
    @Column(name = "regions", nullable = false)
    private @NotNull ConcurrentList<String> regionIds = Concurrent.newList();

    /**
     * What each tier grants, as {@link Stat.Substitute} references whose values are keyed by the tier
     * that grants them.
     */
    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    /**
     * The price ladder, one entry per tier.
     */
    @Column(name = "unlocks", nullable = false)
    private @NotNull ConcurrentList<Unlock> unlocks = Concurrent.newList();

    /**
     * The {@link Region} rows behind {@link #regionIds}, filled in by the repository layer rather
     * than stored in a column.
     */
    @ForeignIds("regionIds")
    private transient @NotNull ConcurrentList<Region> regions = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        ShopPerk that = (ShopPerk) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getRegionIds(), that.getRegionIds())
            && Objects.equals(this.getStats(), that.getStats())
            && Objects.equals(this.getUnlocks(), that.getUnlocks());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getRegionIds(), this.getStats(), this.getUnlocks());
    }

    /**
     * One rung of a perk's price ladder - the tier being bought and what it costs.
     */
    @Getter
    @GsonType
    public static class Unlock {

        /**
         * The tier being bought, counted from one.
         */
        private int tier;

        /**
         * What the tier costs, quoted as an {@link Item.Cost} even though an Essence Shop only ever
         * charges essence.
         */
        private @NotNull Item.Cost cost = new Item.Cost();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Unlock that = (Unlock) o;

            return this.getTier() == that.getTier()
                && Objects.equals(this.getCost(), that.getCost());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getTier(), this.getCost());
        }

    }

}