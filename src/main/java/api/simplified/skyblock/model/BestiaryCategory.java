package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
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
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

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
@Table(name = "bestiary_categories")
public class BestiaryCategory implements JpaModel {

    /**
     * The category's own id, the value a {@link BestiaryFamily} names as its category.
     */
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

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "region_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Region region;

    /**
     * The resolved {@link Region} behind the category's region id, empty for a category that is not
     * a place.
     */
    public @NotNull Optional<Region> getRegion() {
        return Optional.ofNullable(this.region);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        BestiaryCategory that = (BestiaryCategory) o;

        return this.getOrdinal() == that.getOrdinal()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getRegionId(), that.getRegionId())
            && Objects.equals(this.getFormat(), that.getFormat());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getRegionId(), this.getFormat(), this.getOrdinal());
    }

}
