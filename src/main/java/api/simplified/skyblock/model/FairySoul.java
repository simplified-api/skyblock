package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

/**
 * One fairy soul's location - the collectibles scattered across the world that grant SkyBlock
 * experience and, once exchanged with Tia the Fairy, permanent stat upgrades.
 *
 * <p>
 * A soul is identified by where it is rather than by its index, so two souls recorded at the same
 * coordinates in the same zone are equal. No locations are supplied today, so a lookup finds
 * nothing until the data source carries them.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Fairy_Souls">Fairy Souls</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = { "id", "zone" })
@Table(name = "fairy_souls")
public class FairySoul implements JpaModel {

    /**
     * A numeric index for the soul, and the row's primary key.
     */
    @Id
    @Column(name = "id", nullable = false)
    private int id = 0;

    /**
     * The soul's world x coordinate.
     */
    @Column(name = "x", nullable = false)
    private double x = 0;

    /**
     * The soul's world y coordinate.
     */
    @Column(name = "y", nullable = false)
    private double y = 0;

    /**
     * The soul's world z coordinate.
     */
    @Column(name = "z", nullable = false)
    private double z = 0;

    /**
     * Whether the soul can be reached on foot rather than needing a launch or a flight.
     */
    @Column(name = "walkable", nullable = false)
    private boolean walkable = false;

    /**
     * Id of the zone the soul sits in, bound from the {@code zone} key.
     */
    @SerializedName("zone")
    @Column(name = "zone_id", nullable = false)
    private @NotNull String zoneId = "";

    /**
     * The {@link Zone} resolved from {@link #zoneId}, mapped read-only onto the same column.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "zone_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Zone zone;

}