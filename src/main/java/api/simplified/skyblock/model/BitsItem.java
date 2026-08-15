package api.simplified.skyblock.model;

import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
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
import org.jetbrains.annotations.NotNull;

/**
 * One line of the bits shop - the Community Center stock sold for bits, the currency a member earns
 * while a Booster Cookie is active.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Bits">Bits</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
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
    @EqualsAndHashCode(useAccessors = true)
    public static class Variant {

        /**
         * The variant's item id.
         */
        private @NotNull String id = "";

        /**
         * The variant's own price in bits.
         */
        private int cost;

    }

}
