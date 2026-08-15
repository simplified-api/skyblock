package api.simplified.skyblock.model;

import api.simplified.skyblock.common.Rarity;
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
 * A potion modifier, brewed in place of an Awkward Potion so that the finished potion carries an
 * extra stat bonus or ability on top of its own effect.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Brews">Brews</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "brews")
public class Brew implements JpaModel {

    /**
     * The brew's id.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The brew's display name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Tooltip line describing what the brew adds to the potion.
     */
    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    /**
     * Rarity the finished potion takes.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false)
    private @NotNull Rarity rarity = Rarity.COMMON;

    /**
     * Whether this is the amplified spelling of the brew, which strengthens the bonus it grants.
     */
    @Column(name = "amplified", nullable = false)
    private boolean amplified = false;

    /**
     * Ingredients and currency needed to brew it, which may be priced in coins, essence, motes,
     * stars, north stars or pelts.
     */
    @Column(name = "cost", nullable = false)
    private @NotNull Item.Cost cost = new Item.Cost();

}
