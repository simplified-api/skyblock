package api.simplified.skyblock.common;

import com.google.gson.annotations.SerializedName;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * The rule set a profile was created under, which cannot be changed afterwards.
 * <p>
 * A profile that names no mode on the wire is a classic one, so {@link #CLASSIC} carries no
 * serialized name of its own. The other three are spelled by the wire, and {@link #STRANDED} is
 * spelled {@code island} rather than after the mode's own name.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Gamemodes">Gamemodes</a>
 */
public enum GameMode {

    /**
     * The default mode, with the bazaar, the auction house and trading all available.
     */
    CLASSIC,

    /**
     * A solitary mode that bars the bazaar, the auction house and every other player trade.
     */
    @SerializedName("ironman")
    IRONMAN,

    /**
     * A mode begun on a bare island with no resources granted, spelled {@code island} on the wire.
     */
    @SerializedName("island")
    STRANDED,

    /**
     * A temporary event profile scored against a card of objectives.
     */
    @SerializedName("bingo")
    BINGO;

    /**
     * The mode's name in the capitalisation the game displays.
     */
    public @NotNull String getName() {
        return StringUtil.capitalizeFully(this.name());
    }

}
