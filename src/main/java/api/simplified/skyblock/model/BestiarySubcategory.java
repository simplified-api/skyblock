package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
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

/**
 * A second-level grouping inside a {@link BestiaryCategory}, used where one place holds several
 * distinct groups of mobs.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Bestiary">Bestiary</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = "category")
@Table(name = "bestiary_subcategories")
public class BestiarySubcategory implements JpaModel {

    /**
     * The subcategory's id, the value a {@link BestiaryFamily} names as its subcategory.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The label shown in the menu.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The colour that label is drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GREEN;

    /**
     * The owning category, bound from the key {@code category}.
     */
    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "";

    /**
     * The subcategory's slot in the menu order, {@code -1} for unplaced.
     */
    @Column(name = "ordinal", nullable = false)
    private int ordinal = -1;

    /**
     * The resolved {@link BestiaryCategory} behind the category id.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull BestiaryCategory category;

}
