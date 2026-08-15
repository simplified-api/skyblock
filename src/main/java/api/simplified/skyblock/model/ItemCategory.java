package api.simplified.skyblock.model;

import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

/**
 * The category an item is filed under in menus and in the auction house search.
 *
 * <p>
 * It is finer-grained than {@link Item.Type}, which is what a category rolls up to. {@code NONE} and
 * {@code OTHER} are ordinary rows here rather than sentinels, so an item filed under either still
 * resolves to a real category.
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "item_categories")
public class ItemCategory implements JpaModel {

    /**
     * The category's id, and the key an {@link Item}, an {@link Enchantment} or a {@link Reforge}
     * names when it points at a category.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The name the category is labelled with.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The coarse grouping this category rolls up to.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private @NotNull Item.Type type = Item.Type.OTHER;

}