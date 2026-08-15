package api.simplified.skyblock.date;

import dev.simplified.annotations.EnumLookup;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.KeyField;
import dev.simplified.annotations.RequiredArgsConstructor;
import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.time.Month;

/**
 * One of the twelve months of the Hypixel SkyBlock year.
 *
 * <p>
 * A SkyBlock year is 12 months of 31 days, and every month is a named season - three each for
 * spring, summer, autumn and winter, in calendar order. Each constant carries its own month number,
 * counting from one so that it lines up with {@link Month} and with how the game numbers days, and
 * that number is the only thing {@link SkyBlockDate} reads. Declaration order carries no meaning of
 * its own.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Calendar">Calendar</a>
 */
@Getter
@EnumLookup
@RequiredArgsConstructor
public enum Season {

    /**
     * The first month of the SkyBlock year, opening spring.
     */
    EARLY_SPRING(1, "Early Spring", ChatColor.Legacy.GREEN),

    /**
     * The second month of the SkyBlock year, the middle of spring.
     */
    SPRING(2, "Spring", ChatColor.Legacy.GREEN),

    /**
     * The third month of the SkyBlock year, closing spring and carrying the day a mayor term turns
     * over.
     */
    LATE_SPRING(3, "Late Spring", ChatColor.Legacy.GREEN),

    /**
     * The fourth month of the SkyBlock year, opening summer.
     */
    EARLY_SUMMER(4, "Early Summer", ChatColor.Legacy.YELLOW),

    /**
     * The fifth month of the SkyBlock year, the middle of summer.
     */
    SUMMER(5, "Summer", ChatColor.Legacy.YELLOW),

    /**
     * The sixth month of the SkyBlock year, closing summer and carrying the day mayor voting opens.
     */
    LATE_SUMMER(6, "Late Summer", ChatColor.Legacy.YELLOW),

    /**
     * The seventh month of the SkyBlock year, opening autumn.
     */
    EARLY_AUTUMN(7, "Early Autumn", ChatColor.Legacy.GOLD),

    /**
     * The eighth month of the SkyBlock year, the middle of autumn.
     */
    AUTUMN(8, "Autumn", ChatColor.Legacy.GOLD),

    /**
     * The ninth month of the SkyBlock year, closing autumn.
     */
    LATE_AUTUMN(9, "Late Autumn", ChatColor.Legacy.GOLD),

    /**
     * The tenth month of the SkyBlock year, opening winter.
     */
    EARLY_WINTER(10, "Early Winter", ChatColor.Legacy.AQUA),

    /**
     * The eleventh month of the SkyBlock year, the middle of winter.
     */
    WINTER(11, "Winter", ChatColor.Legacy.AQUA),

    /**
     * The twelfth and last month of the SkyBlock year, closing winter.
     */
    LATE_WINTER(12, "Late Winter", ChatColor.Legacy.AQUA);

    /**
     * The season's month number, counting from one.
     */
    @KeyField(strictKeys = true)
    private final int month;

    /**
     * Name of the season as the game spells it.
     */
    private final @NotNull String name;

    /**
     * {@link ChatColor} the game renders this season's name in.
     */
    private final @NotNull ChatColor format;

    /**
     * Finds the season a month number names.
     *
     * @param month the month number, counting from one
     * @return the season of that month
     * @throws IllegalArgumentException if no season carries that month number
     */
    public static @NotNull Season of(int month) {
        return findByMonth(month).orElseThrow(() -> new IllegalArgumentException(String.format("No season with month '%s'", month)));
    }

}
