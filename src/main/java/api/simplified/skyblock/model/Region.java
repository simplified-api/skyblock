package api.simplified.skyblock.model;

import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A region - one of the top-level worlds a member can be in, such as the Hub, the Dwarven Mines, the
 * Crimson Isle or the Rift. It is the coarse half of the world model and {@link Zone} is the fine
 * half.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Locations">Locations</a>
 */
@Getter
@Entity
@Table(name = "regions")
public class Region implements JpaModel {

    /**
     * The region's id.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the region.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The colour the region's name is drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GRAY;

    /**
     * The Hypixel game type reported for the region, so it can be matched against a player's online
     * status.
     */
    @Column(name = "game_type", nullable = false)
    private @NotNull String gameType = "";

    /**
     * The Hypixel server mode reported for the region, the finer half of that same match.
     */
    @Column(name = "mode", nullable = false)
    private @NotNull String mode = "";

    /**
     * Zones this region contains, owned by {@link Zone#region} and populated by the provider,
     * which supplies its own list implementation. It takes no part in equality, so region identity is
     * the bound columns alone.
     */
    @OneToMany(mappedBy = "region")
    private @NotNull List<Zone> zones = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Region that = (Region) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getGameType(), that.getGameType())
            && Objects.equals(this.getMode(), that.getMode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getFormat(), this.getGameType(), this.getMode());
    }

}