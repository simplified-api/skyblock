package dev.sbs.minecraftapi.skyblock.date;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * The season of the year, matches up with current month of the year.
 */
@Getter
@RequiredArgsConstructor
public enum Season {

    EARLY_SPRING("Early Spring"),
    SPRING("Spring"),
    LATE_SPRING("Late Spring"),
    EARLY_SUMMER("Early Summer"),
    SUMMER("Summer"),
    LATE_SUMMER("Late Summer"),
    EARLY_AUTUMN("Early Autumn"),
    AUTUMN("Autumn"),
    LATE_AUTUMN("Late Autumn"),
    EARLY_WINTER("Early Winter"),
    WINTER("Winter"),
    LATE_WINTER("Late Winter");

    private final @NotNull String name;

}
