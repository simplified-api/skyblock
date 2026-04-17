package dev.sbs.minecraftapi.skyblock.common;

import com.google.gson.annotations.SerializedName;
import dev.sbs.renderer.text.ChatColor;
import dev.simplified.util.StringUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum Rarity {

    COMMON(ChatColor.Legacy.WHITE, 3, false, true),
    UNCOMMON(ChatColor.Legacy.GREEN, 5, false, true),
    RARE(ChatColor.Legacy.BLUE, 8, false, true),
    EPIC(ChatColor.Legacy.DARK_PURPLE, 12, true, true),
    LEGENDARY(ChatColor.Legacy.GOLD, 16, true, true),
    MYTHIC(ChatColor.Legacy.LIGHT_PURPLE, 22, true, false),
    @SerializedName(alternate = { "SUPREME" }, value = "DIVINE")
    DIVINE(ChatColor.Legacy.AQUA, 0, true, false),
    SPECIAL(ChatColor.Legacy.RED, 3, true, true),
    VERY_SPECIAL(ChatColor.Legacy.RED, 5, true, false),
    ULTIMATE(ChatColor.Legacy.DARK_RED, 0, false, false),
    @SerializedName(alternate = { "UNOBTAINABLE" }, value = "ADMIN")
    ADMIN(ChatColor.Legacy.DARK_RED, 0, false, false);

    private final @NotNull ChatColor format;
    private final int magicPower;
    private final boolean enrichable;
    private final boolean recombobulatable;

    public @NotNull String getName() {
        return StringUtil.capitalizeFully(this.name());
    }

    public static @NotNull Rarity of(@NotNull String name) {
        return Arrays.stream(values())
            .filter(rarity -> rarity.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No rarity with name " + name));
    }

    public static @NotNull Rarity of(int ordinal) {
        return Arrays.stream(values())
            .filter(rarity -> rarity.ordinal() == ordinal)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No rarity with ordinal " + ordinal));
    }

}
