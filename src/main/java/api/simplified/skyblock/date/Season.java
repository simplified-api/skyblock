package api.simplified.skyblock.date;

import lib.minecraft.text.ChatColor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * One of the twelve months of the Hypixel SkyBlock year.
 *
 * <p>
 * A SkyBlock year is 12 months of 31 days, and every month is a named season - three each
 * for spring, summer, autumn and winter, in declaration order. The ordinal is the month
 * number less one, which is how {@link SkyBlockDate} converts a season into a calendar
 * month and back.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Calendar">Calendar</a>
 */
@Getter
@RequiredArgsConstructor
public enum Season {

    /**
     * The first month of the SkyBlock year, opening spring.
     */
    EARLY_SPRING("Early Spring", ChatColor.Legacy.GREEN),

    /**
     * The second month of the SkyBlock year, the middle of spring.
     */
    SPRING("Spring", ChatColor.Legacy.GREEN),

    /**
     * The third month of the SkyBlock year, closing spring and carrying the day a mayor
     * term turns over.
     */
    LATE_SPRING("Late Spring", ChatColor.Legacy.GREEN),

    /**
     * The fourth month of the SkyBlock year, opening summer.
     */
    EARLY_SUMMER("Early Summer", ChatColor.Legacy.YELLOW),

    /**
     * The fifth month of the SkyBlock year, the middle of summer.
     */
    SUMMER("Summer", ChatColor.Legacy.YELLOW),

    /**
     * The sixth month of the SkyBlock year, closing summer and carrying the day mayor
     * voting opens.
     */
    LATE_SUMMER("Late Summer", ChatColor.Legacy.YELLOW),

    /**
     * The seventh month of the SkyBlock year, opening autumn.
     */
    EARLY_AUTUMN("Early Autumn", ChatColor.Legacy.GOLD),

    /**
     * The eighth month of the SkyBlock year, the middle of autumn.
     */
    AUTUMN("Autumn", ChatColor.Legacy.GOLD),

    /**
     * The ninth month of the SkyBlock year, closing autumn.
     */
    LATE_AUTUMN("Late Autumn", ChatColor.Legacy.GOLD),

    /**
     * The tenth month of the SkyBlock year, opening winter.
     */
    EARLY_WINTER("Early Winter", ChatColor.Legacy.AQUA),

    /**
     * The eleventh month of the SkyBlock year, the middle of winter.
     */
    WINTER("Winter", ChatColor.Legacy.AQUA),

    /**
     * The twelfth and last month of the SkyBlock year, closing winter.
     */
    LATE_WINTER("Late Winter", ChatColor.Legacy.AQUA);

    /**
     * Name of the season as the game spells it.
     */
    private final @NotNull String name;

    /**
     * {@link ChatColor} the game renders this season's name in.
     */
    private final @NotNull ChatColor format;

}
