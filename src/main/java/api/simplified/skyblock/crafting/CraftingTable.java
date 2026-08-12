package api.simplified.skyblock.crafting;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Range;

/**
 * The nine positions of a crafting grid, each carrying where it sits in the menu that draws it.
 * <p>
 * SkyBlock's crafting table is a chest menu rather than a real crafting window, so the grid is nine
 * slots cut out of a wider inventory. The three rows are nine apart because the menu is nine columns
 * wide, which is why a position's slot number is not its position in the recipe.
 */
@Getter
@RequiredArgsConstructor
public enum CraftingTable {

    /**
     * The top left corner of the grid.
     */
    TOP_LEFT(10),

    /**
     * The top middle position of the grid.
     */
    TOP_CENTER(11),

    /**
     * The top right corner of the grid.
     */
    TOP_RIGHT(12),

    /**
     * The middle left position of the grid.
     */
    MIDDLE_LEFT(19),

    /**
     * The centre of the grid.
     */
    MIDDLE_CENTER(20),

    /**
     * The middle right position of the grid.
     */
    MIDDLE_RIGHT(21),

    /**
     * The bottom left corner of the grid.
     */
    BOTTOM_LEFT(28),

    /**
     * The bottom middle position of the grid.
     */
    BOTTOM_CENTER(29),

    /**
     * The bottom right corner of the grid.
     */
    BOTTOM_RIGHT(30);

    /**
     * Where this position sits in the nine-column menu that draws the grid.
     */
    private final int slot;

    /**
     * The slot rebased against a nine-column menu, so the top left corner sits at zero.
     */
    public int getZeroSlot() {
        return this.getZeroSlot(9);
    }

    /**
     * Rebases the slot against a menu of a given width so the top left corner sits at zero.
     *
     * @param rowSize the number of columns in the menu drawing the grid
     * @return the rebased slot
     */
    public int getZeroSlot(@Range(from = 3, to = 9) int rowSize) {
        return this.getSlot() - 10 - (rowSize - 3);
    }

}
