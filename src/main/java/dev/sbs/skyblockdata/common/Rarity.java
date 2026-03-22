package dev.sbs.minecraftapi.skyblock.common;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.util.StringUtil;
import dev.sbs.minecraftapi.render.text.ChatFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum Rarity {

    COMMON(ChatFormat.WHITE, 3, false, true),
    UNCOMMON(ChatFormat.GREEN, 5, false, true),
    RARE(ChatFormat.BLUE, 8, false, true),
    EPIC(ChatFormat.DARK_PURPLE, 12, true, true),
    LEGENDARY(ChatFormat.GOLD, 16, true, true),
    MYTHIC(ChatFormat.LIGHT_PURPLE, 22, true, false),
    @SerializedName(alternate = { "SUPREME" }, value = "DIVINE")
    DIVINE(ChatFormat.AQUA, 0, true, false),
    SPECIAL(ChatFormat.RED, 3, true, true),
    VERY_SPECIAL(ChatFormat.RED, 5, true, false),
    ULTIMATE(ChatFormat.DARK_RED, 0, false, false),
    @SerializedName(alternate = { "UNOBTAINABLE" }, value = "ADMIN")
    ADMIN(ChatFormat.DARK_RED, 0, false, false);

    private final @NotNull ChatFormat format;
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
