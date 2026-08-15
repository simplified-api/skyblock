package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
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
import org.jetbrains.annotations.NotNull;

/**
 * A zone - one named area inside a region, the granularity the game shows in the sidebar and the
 * granularity a fairy soul or a shop perk description is located by. It is the fine half of the
 * world model and {@link Region} is the coarse half.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Locations">Locations</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = "region")
@Table(name = "zones")
public class Zone implements JpaModel {

    /**
     * The zone's id, the token a description writes as {@code %{ZONE:THE_CATACOMBS}}. It can equal
     * the owning region's id where that region holds a single area.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the zone.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The colour the zone's name is drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GRAY;

    /**
     * Id of the region the zone sits in, bound from the wire key {@code region}.
     */
    @SerializedName("region")
    @Column(name = "region_id", nullable = false)
    private @NotNull String regionId = "";

    /**
     * The {@link Region} row behind {@link #regionId}, resolved on the same column and the inverse of
     * {@link Region#zones}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "region_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Region region;

}