package api.simplified.skyblock.model;

import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.persistence.Hydration;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * A region - one of the top-level worlds a member can be in, such as the Hub, the Dwarven Mines, the
 * Crimson Isle or the Rift. It is the coarse half of the world model and {@link Zone} is the fine
 * half.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Locations">Locations</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "regions")
@Hydration(every = 10, unit = TimeUnit.MINUTES)
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

}