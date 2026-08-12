package api.simplified.skyblock.crafting;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import static api.simplified.skyblock.crafting.CraftingTable.*;

/**
 * The slot patterns a crafting recipe can occupy, named after the shape each one makes.
 * <p>
 * A pattern says only which of the nine positions a recipe uses, not what goes in them, so
 * one pattern is shared by every recipe laid out the same way.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Crafting">Crafting</a>
 */
@Getter
public enum CraftingRecipe {

    /**
     * Every slot filled, the shape of a recipe using all nine.
     */
    ALL(
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    ),

    /**
     * A two by two square in the top left corner.
     */
    BOX(
        TOP_LEFT,
        TOP_CENTER,
        MIDDLE_LEFT,
        MIDDLE_CENTER
    ),

    /**
     * The two middle sides and the bottom centre, the shape a bucket is crafted in.
     */
    BUCKET(
        MIDDLE_LEFT,
        BOTTOM_CENTER,
        MIDDLE_RIGHT
    ),

    /**
     * The centre and the slot below it.
     */
    CENTER2(
        MIDDLE_CENTER,
        BOTTOM_CENTER
    ),

    /**
     * The whole middle column.
     */
    CENTER3(
        TOP_CENTER,
        MIDDLE_CENTER,
        BOTTOM_CENTER
    ),

    /**
     * A diagonal running from the bottom left to the top right.
     */
    DIAGONAL(
        BOTTOM_LEFT,
        MIDDLE_CENTER,
        TOP_RIGHT
    ),

    /**
     * The top two rows filled.
     */
    DOUBLE_ROW(
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT
    ),

    /**
     * A two by two square in the bottom right corner.
     */
    ENCHANT(
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    ),

    /**
     * A two by two square in the bottom right corner with the top left corner added.
     */
    ENCHANT_FISH(
        TOP_LEFT,
        MIDDLE_CENTER,
        MIDDLE_RIGHT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    ),

    /**
     * The two sides, the centre and the bottom centre, the shape a hopper is crafted in.
     */
    HOPPER(
        TOP_LEFT,
        MIDDLE_LEFT,
        BOTTOM_CENTER,
        MIDDLE_RIGHT,
        TOP_RIGHT,
        MIDDLE_CENTER
    ),

    /**
     * The middle left position and the centre.
     */
    MIDDLE2(
        MIDDLE_LEFT,
        MIDDLE_CENTER
    ),

    /**
     * Every slot but the centre.
     */
    RING(
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    ),

    /**
     * One slot in the top left corner.
     */
    SINGLE(
        TOP_LEFT
    ),

    /**
     * The top row filled.
     */
    SINGLE_ROW(
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT
    ),

    /**
     * The four sides around a filled centre.
     */
    STAR(
        TOP_CENTER,
        MIDDLE_LEFT,
        MIDDLE_RIGHT,
        BOTTOM_CENTER,
        MIDDLE_CENTER
    ),

    /**
     * The top row filled, the same shape as a single row.
     */
    TOP_ROW(
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT
    ),

    /**
     * The middle left position, the centre and the bottom left corner.
     */
    TRIANGLE(
        MIDDLE_LEFT,
        MIDDLE_CENTER,
        BOTTOM_LEFT
    ),

    /**
     * No slots at all, for anything that is not crafted.
     */
    NONE;

    /**
     * The grid positions this pattern fills, in the order the pattern declares them.
     */
    private final @NotNull ConcurrentList<CraftingTable> slots;

    /**
     * Constructs a new {@code CraftingRecipe} filling the given positions.
     *
     * @param slots the grid positions this pattern fills
     */
    CraftingRecipe(@NotNull CraftingTable... slots) {
        this.slots = Concurrent.newList(slots);
    }

}
