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
import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;

/**
 * An essence type - the currency spent at Essence Shops to upgrade items and buy permanent perks,
 * with one type per source, mostly per dungeon boss or per region.
 *
 * <p>
 * The row is pure vocabulary: it carries no exchange rate and no shop contents, since what essence
 * buys is a {@link ShopPerk}.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Essence">Essence</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "essences")
public class Essence implements JpaModel {

    /**
     * The essence id, which is also the key a member's essence totals are stored under.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The essence's display name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Colour the name is drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.LIGHT_PURPLE;

}
