package dev.sbs.minecraftapi.skyblock.common;

import com.google.gson.annotations.SerializedName;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public enum GameMode {

    CLASSIC,
    @SerializedName("ironman")
    IRONMAN,
    @SerializedName("island")
    STRANDED,
    @SerializedName("bingo")
    BINGO;

    public @NotNull String getName() {
        return StringUtil.capitalizeFully(this.name());
    }

}
