package api.simplified.skyblock.model;

import api.simplified.skyblock.common.Rarity;
import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A gemstone type - the mining-collection stones slotted into gear that has gemstone slots, each
 * granting one stat scaled by both its own cut and the rarity of the slot.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Gemstone">Gemstone</a>
 */
@Getter
@Entity
@Table(name = "gemstones")
public class Gemstone implements JpaModel {

    /**
     * The gemstone's id.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The gemstone's display name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The glyph drawn beside the gemstone in a tooltip.
     */
    @Column(name = "symbol", nullable = false)
    private @NotNull String symbol = "";

    /**
     * Colour that glyph and the name are drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.WHITE;

    /**
     * Id of the single stat the gemstone grants, bound from the {@code stat} key.
     */
    @SerializedName("stat")
    @Column(name = "stat_id", nullable = false)
    private @NotNull String statId = "";

    /**
     * The grant, keyed first by cut and then by the rarity of the slot. A combination absent from
     * the map is one that does not exist rather than a grant of zero.
     */
    @Column(name = "values", nullable = false)
    private @NotNull ConcurrentMap<Type, ConcurrentMap<Rarity, Double>> values = Concurrent.newMap();

    /**
     * The {@link Stat} resolved from {@link #statId}, mapped read-only onto the same column.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "stat_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Stat stat;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Gemstone that = (Gemstone) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getSymbol(), that.getSymbol())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getStatId(), that.getStatId())
            && Objects.equals(this.getValues(), that.getValues());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getSymbol(), this.getFormat(), this.getStatId(), this.getValues());
    }

    /**
     * The cuts a gemstone can take, weakest to strongest.
     */
    public enum Type {

        /**
         * The uncut stone, straight from the collection.
         */
        ROUGH,

        /**
         * The first cut.
         */
        FLAWED,

        /**
         * The second cut.
         */
        FINE,

        /**
         * The third cut.
         */
        FLAWLESS,

        /**
         * The final cut.
         */
        PERFECT

    }

}