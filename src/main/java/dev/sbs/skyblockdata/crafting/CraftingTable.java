package dev.sbs.minecraftapi.skyblock.crafting;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Range;

@Getter
@RequiredArgsConstructor
public enum CraftingTable {

    TOP_LEFT(10),
    TOP_CENTER(11),
    TOP_RIGHT(12),

    MIDDLE_LEFT(19),
    MIDDLE_CENTER(20),
    MIDDLE_RIGHT(21),

    BOTTOM_LEFT(28),
    BOTTOM_CENTER(29),
    BOTTOM_RIGHT(30);

    private final int slot;

    public int getZeroSlot() {
        return this.getZeroSlot(9);
    }

    public int getZeroSlot(@Range(from = 3, to = 9) int rowSize) {
        return this.getSlot() - 10 - (rowSize - 3);
    }

}
