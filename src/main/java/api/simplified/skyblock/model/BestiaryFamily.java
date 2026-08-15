package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.ForeignIds;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * One entry in the Bestiary - a family of related mobs whose kills accumulate together toward 25
 * tiers of milestone rewards.
 *
 * <p>
 * This is the row that says how many kills each tier costs: {@code bracket} picks one of the
 * {@link #BRACKETS} ladders and {@code maxTier} says how far along it this family goes. Kill totals
 * are cumulative and do not reset as tiers are reached.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Bestiary">Bestiary</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = { "category", "subcategory" })
@Table(name = "bestiary_families")
public class BestiaryFamily implements JpaModel {

    /**
     * The family's id.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The family name shown in the menu.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The flavour line printed under the family name.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * The colour the family name is drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GREEN;

    /**
     * Which of the {@link #BRACKETS} kill-count ladders this family charges against, counting from
     * one - rarer mobs sit on a cheaper bracket.
     */
    @Column(name = "bracket", nullable = false)
    private int bracket = 1;

    /**
     * How many of that ladder's tiers this family actually offers, counting from one.
     */
    @Column(name = "max_tier", nullable = false)
    private int maxTier = 25;

    /**
     * The owning category, bound from the key {@code category}.
     */
    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "";

    /**
     * An optional grouping inside the category, bound from the key {@code subcategory}.
     */
    @SerializedName("subcategory")
    @Column(name = "subcategory_id")
    private @NotNull Optional<String> subcategoryId = Optional.empty();

    /**
     * The mob types this family counts as, bound from the key {@code mobTypes}.
     */
    @SerializedName("mobTypes")
    @Column(name = "mob_types", nullable = false)
    private @NotNull ConcurrentList<String> mobTypeIds = Concurrent.newUnmodifiableList();

    /**
     * The internal spawn ids whose kills feed this family, and so the link back to the kill counters
     * a member's Bestiary reports.
     */
    @Column(name = "mobs", nullable = false)
    private @NotNull ConcurrentList<String> mobs = Concurrent.newUnmodifiableList();

    /**
     * The resolved {@link BestiaryCategory} behind the category id.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull BestiaryCategory category;

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "subcategory_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable BestiarySubcategory subcategory;

    /**
     * The resolved {@link MobType} rows behind the mob type ids, filled in by the repository layer
     * rather than by a column.
     */
    @ForeignIds("mobTypeIds")
    private transient @NotNull ConcurrentList<MobType> mobTypes = Concurrent.newList();

    /**
     * The kill count of this family's final tier, and so the total needed to finish it.
     */
    public int getMaxKills() {
        return BRACKETS
            .get(this.getBracket() - 1)
            .get(this.getMaxTier() - 1);
    }

    /**
     * The resolved {@link BestiarySubcategory} behind the subcategory id, empty for the families
     * that are not grouped further.
     */
    public @NotNull Optional<BestiarySubcategory> getSubcategory() {
        return Optional.ofNullable(this.subcategory);
    }

    /**
     * The kill-count ladder this family charges against, one entry per tier.
     */
    public @NotNull ConcurrentList<Integer> getTiers() {
        return BRACKETS.get(this.getBracket() - 1);
    }

    /**
     * The seven kill-count ladders every family charges against, each 25 tiers long and ordered from
     * the dearest bracket to the cheapest - a family of common mobs pays 20 kills for its first
     * tier where a family of rare ones pays one.
     */
    public static final @NotNull ConcurrentList<ConcurrentList<Integer>> BRACKETS = Concurrent.newUnmodifiableList(
        Concurrent.newUnmodifiableList(
            20, 40, 60, 100, 200,
            400, 800, 1_400, 2_000, 3_000,
            6_000, 12_000, 20_000, 30_000, 40_000,
            50_000, 60_000, 72_000, 86_000, 100_000,
            200_000, 400_000, 600_000, 800_000, 1_000_000
        ),
        Concurrent.newUnmodifiableList(
            5, 10, 15, 25, 50,
            100, 200, 350, 500, 750,
            1_500, 3_000, 5_000, 7_500, 10_000,
            12_500, 15_000, 18_000, 21_500, 25_000,
            50_000, 100_000, 150_000, 200_000, 250_000
        ),
        Concurrent.newUnmodifiableList(
            4, 8, 12, 16, 20,
            40, 80, 140, 200, 300,
            600, 1_200, 2_000, 3_000, 4_000,
            5_000, 6_000, 7_200, 8_600, 10_000,
            20_000, 40_000, 60_000, 80_000, 100_000
        ),
        Concurrent.newUnmodifiableList(
            2, 4, 6, 10, 15,
            20, 25, 35, 50, 75,
            150, 300, 500, 750, 1_000,
            1_350, 1_650, 2_000, 2_500, 3_000,
            5_000, 10_000, 15_000, 20_000, 25_000
        ),
        Concurrent.newUnmodifiableList(
            1, 2, 3, 5, 7,
            10, 15, 20, 25, 30,
            60, 120, 200, 300, 400,
            500, 600, 720, 860, 1_000,
            2_000, 4_000, 6_000, 8_000, 10_000
        ),
        Concurrent.newUnmodifiableList(
            1, 2, 3, 5, 7,
            9, 14, 17, 21, 25,
            50, 80, 125, 175, 250,
            325, 425, 525, 625, 750,
            1_500, 3_000, 4_500, 6_000, 7_500
        ),
        Concurrent.newUnmodifiableList(
            1, 2, 3, 5, 7,
            9, 11, 14, 17, 20,
            30, 40, 55, 75, 100,
            150, 200, 275, 375, 500,
            1_000, 1_500, 2_000, 2_500, 3_000
        )
    );

}
