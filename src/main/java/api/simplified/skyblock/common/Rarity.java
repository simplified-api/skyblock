package api.simplified.skyblock.common;

import com.google.gson.annotations.SerializedName;
import dev.simplified.util.StringUtil;
import lib.minecraft.text.ChatColor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * The tiers an item, pet or accessory can hold, ordered from lowest to highest.
 * <p>
 * Declaration order is the ranking, and the ordinal is what a rarity upgrade moves through - a
 * recombobulator or a tier boost raises an item by one position in this list. Two constants carry an
 * alternate wire spelling because the game renamed them without renaming the data.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Rarity">Rarity</a>
 */
@Getter
@RequiredArgsConstructor
public enum Rarity {

    /**
     * The lowest tier, shown in white.
     */
    COMMON(ChatColor.Legacy.WHITE, 3, false, true),

    /**
     * One above common, shown in green.
     */
    UNCOMMON(ChatColor.Legacy.GREEN, 5, false, true),

    /**
     * The middle tier, shown in blue.
     */
    RARE(ChatColor.Legacy.BLUE, 8, false, true),

    /**
     * The first tier an accessory enrichment can be applied at, shown in dark purple.
     */
    EPIC(ChatColor.Legacy.DARK_PURPLE, 12, true, true),

    /**
     * The highest tier most items reach, shown in gold.
     */
    LEGENDARY(ChatColor.Legacy.GOLD, 16, true, true),

    /**
     * A tier above legendary, reached only by recombobulating, shown in light purple.
     */
    MYTHIC(ChatColor.Legacy.LIGHT_PURPLE, 22, true, false),

    /**
     * A tier above mythic, spelled {@code SUPREME} in older data.
     */
    @SerializedName(alternate = { "SUPREME" }, value = "DIVINE")
    DIVINE(ChatColor.Legacy.AQUA, 0, true, false),

    /**
     * A tier outside the ordinary ladder, used for event and cosmetic items.
     */
    SPECIAL(ChatColor.Legacy.RED, 3, true, true),

    /**
     * A tier above special, on the same side ladder.
     */
    VERY_SPECIAL(ChatColor.Legacy.RED, 5, true, false),

    /**
     * The tier carried by ultimate enchantment books.
     */
    ULTIMATE(ChatColor.Legacy.DARK_RED, 0, false, false),

    /**
     * A tier no player can obtain, spelled {@code UNOBTAINABLE} in older data.
     */
    @SerializedName(alternate = { "UNOBTAINABLE" }, value = "ADMIN")
    ADMIN(ChatColor.Legacy.DARK_RED, 0, false, false);

    /**
     * Colour the game shows this tier in.
     */
    private final @NotNull ChatColor format;

    /**
     * Magical power an accessory of this tier contributes, zero for the tiers no accessory reaches.
     */
    private final int magicPower;

    /**
     * Whether an accessory of this tier can carry an enrichment.
     */
    private final boolean enrichable;

    /**
     * Whether an item of this tier can still be recombobulated to the tier above.
     */
    private final boolean recombobulatable;

    /**
     * The tier's name in the capitalisation the game displays.
     */
    public @NotNull String getName() {
        return StringUtil.capitalizeFully(this.name());
    }

    /**
     * Finds the tier of a given name, ignoring case.
     *
     * @param name the tier name to match
     * @return the matching tier
     * @throws IllegalArgumentException if no tier carries that name
     */
    public static @NotNull Rarity of(@NotNull String name) {
        return Arrays.stream(values())
            .filter(rarity -> rarity.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No rarity with name " + name));
    }

    /**
     * Finds the tier at a given position in the ladder.
     * <p>
     * This is how a rarity upgrade resolves - an item's own ordinal plus one for each upgrade it
     * carries.
     *
     * @param ordinal the position to read
     * @return the tier at that position
     * @throws IllegalArgumentException if no tier sits at that position
     */
    public static @NotNull Rarity of(int ordinal) {
        return Arrays.stream(values())
            .filter(rarity -> rarity.ordinal() == ordinal)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No rarity with ordinal " + ordinal));
    }

}
