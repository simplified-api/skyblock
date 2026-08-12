package api.simplified.skyblock.model;

import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A mob classification - undead, arthropod, dragon, airborne and so on.
 *
 * <p>
 * It is the axis a bestiary family, a slayer, an enchantment or a conditional item stat targets
 * whenever it means "these kinds of mobs" rather than one named mob.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Mob">Mob</a>
 */
@Getter
@Entity
@Table(name = "mob_types")
public class MobType implements JpaModel {

    /**
     * The classification's id, and the key a {@link BestiaryFamily}, an {@link Enchantment} or a
     * {@link Slayer} names when it targets this kind of mob.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The name the classification is shown under.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The glyph drawn beside the name.
     */
    @Column(name = "symbol", nullable = false)
    private @NotNull String symbol = "";

    /**
     * The colour the name and glyph are drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.WHITE;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        MobType that = (MobType) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getSymbol(), that.getSymbol())
            && Objects.equals(this.getFormat(), that.getFormat());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getSymbol(), this.getFormat());
    }

}