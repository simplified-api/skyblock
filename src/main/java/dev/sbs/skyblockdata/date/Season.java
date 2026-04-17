package dev.sbs.minecraftapi.skyblock.date;

import dev.sbs.renderer.text.ChatColor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * The season of the year, matches up with current month of the year.
 */
@Getter
@RequiredArgsConstructor
public enum Season {

    EARLY_SPRING("Early Spring", ChatColor.Legacy.GREEN),
    SPRING("Spring", ChatColor.Legacy.GREEN),
    LATE_SPRING("Late Spring", ChatColor.Legacy.GREEN),
    EARLY_SUMMER("Early Summer", ChatColor.Legacy.YELLOW),
    SUMMER("Summer", ChatColor.Legacy.YELLOW),
    LATE_SUMMER("Late Summer", ChatColor.Legacy.YELLOW),
    EARLY_AUTUMN("Early Autumn", ChatColor.Legacy.GOLD),
    AUTUMN("Autumn", ChatColor.Legacy.GOLD),
    LATE_AUTUMN("Late Autumn", ChatColor.Legacy.GOLD),
    EARLY_WINTER("Early Winter", ChatColor.Legacy.AQUA),
    WINTER("Winter", ChatColor.Legacy.AQUA),
    LATE_WINTER("Late Winter", ChatColor.Legacy.AQUA);

    private final @NotNull String name;
    private final @NotNull ChatColor format;

}
