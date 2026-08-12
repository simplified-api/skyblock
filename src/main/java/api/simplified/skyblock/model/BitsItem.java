package api.simplified.skyblock.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import dev.simplified.util.StringUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * One line of the bits shop - the Community Center stock sold for bits, the currency a member earns
 * while a Booster Cookie is active.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Bits">Bits</a>
 */
@Getter
@Entity
@Table(name = "bits_items")
public class BitsItem implements JpaModel {

    /**
     * The id of the item being sold.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The shop tab this listing appears under.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private @NotNull Type type = Type.ITEM;

    /**
     * The price in bits.
     */
    @Column(name = "cost", nullable = false)
    private int cost;

    /**
     * Cheaper or dearer spellings of the same listing, each with its own id and price; empty for
     * most rows.
     */
    @Column(name = "variants", nullable = false)
    private @NotNull ConcurrentList<Variant> variants = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        BitsItem that = (BitsItem) o;

        return this.getCost() == that.getCost()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getType(), that.getType())
            && Objects.equals(this.getVariants(), that.getVariants());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getType(), this.getCost(), this.getVariants());
    }

    /**
     * The tab of the bits shop a listing is filed under.
     */
    public enum Type {

        /**
         * An accessory sold for bits.
         */
        ACCESSORY,

        /**
         * An attribute shard.
         */
        ATTRIBUTE_SHARD,

        /**
         * A single-use item such as a God Potion or a Kismet Feather.
         */
        CONSUMABLE,

        /**
         * A purely visual purchase.
         */
        COSMETIC,

        /**
         * An armour dye.
         */
        DYE,

        /**
         * An enchanted book.
         */
        ENCHANTED_BOOK,

        /**
         * An accessory enrichment.
         */
        ENRICHMENT,

        /**
         * A plain item, and the default.
         */
        ITEM,

        /**
         * A storage sack.
         */
        SACK,

        /**
         * A permanent account or profile upgrade.
         */
        UPGRADE;

        /**
         * The tab's name in title case, with underscores rendered as spaces.
         */
        public @NotNull String getName() {
            return StringUtil.capitalizeFully(this.name().replace("_", " "));
        }

    }

    /**
     * An alternative spelling of a bits shop listing, sold at its own price.
     */
    @Getter
    @GsonType
    public static class Variant {

        /**
         * The variant's item id.
         */
        private @NotNull String id = "";

        /**
         * The variant's own price in bits.
         */
        private int cost;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Variant that = (Variant) o;

            return this.getCost() == that.getCost()
                && Objects.equals(this.getId(), that.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getCost());
        }

    }

}
