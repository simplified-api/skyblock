package dev.sbs.minecraftapi.skyblock.date;

import dev.sbs.renderer.text.ChatFormat;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * The season of the year, matches up with current month of the year.
 */
@Getter
@RequiredArgsConstructor
public enum Season {

    EARLY_SPRING("Early Spring", ChatFormat.GREEN),
    SPRING("Spring", ChatFormat.GREEN),
    LATE_SPRING("Late Spring", ChatFormat.GREEN),
    EARLY_SUMMER("Early Summer", ChatFormat.YELLOW),
    SUMMER("Summer", ChatFormat.YELLOW),
    LATE_SUMMER("Late Summer", ChatFormat.YELLOW),
    EARLY_AUTUMN("Early Autumn", ChatFormat.GOLD),
    AUTUMN("Autumn", ChatFormat.GOLD),
    LATE_AUTUMN("Late Autumn", ChatFormat.GOLD),
    EARLY_WINTER("Early Winter", ChatFormat.AQUA),
    WINTER("Winter", ChatFormat.AQUA),
    LATE_WINTER("Late Winter", ChatFormat.AQUA);

    private final @NotNull String name;
    private final @NotNull ChatFormat format;

}
