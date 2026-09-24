package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.query.Indexed;
import dev.simplified.persistence.Hydration;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.Linked;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * One top-level tab of the Bestiary, the in-game record of a member's kills against every mob.
 *
 * <p>
 * A category is normally a place - Your Island, the Catacombs - which is why most rows name a
 * {@link Region}.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Bestiary">Bestiary</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "bestiary_categories")
@Hydration(every = 10, unit = TimeUnit.MINUTES)
public class BestiaryCategory implements JpaModel {

    /**
     * The category's own id, the value a {@link BestiaryFamily} names as its category.
     */
    @Indexed(unique = true)
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The label the menu shows for the category.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The region this category corresponds to, bound from the key {@code region} and absent for the
     * categories that are not a place.
     */
    @SerializedName("region")
    @Column(name = "region_id")
    private @NotNull Optional<String> regionId = Optional.empty();

    /**
     * The colour the menu draws the category name in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GREEN;

    /**
     * The category's slot in the menu order, {@code -1} for unplaced.
     */
    @Column(name = "ordinal", nullable = false)
    private int ordinal = -1;

    /**
     * The resolved {@link Region} behind the category's region id, empty for a category that is not
     * a place or whose region id names no region.
     */
    @Linked("regionId")
    private transient @NotNull Optional<Region> region = Optional.empty();

}
