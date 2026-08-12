package api.simplified.skyblock.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
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
 * A mayoral candidate in the SkyBlock election - an NPC who, once elected, applies a set of global
 * perks to the whole server for the term.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Mayor_Election">Mayor Election</a>
 */
@Getter
@Entity
@Table(name = "mayors")
public class Mayor implements JpaModel {

    /**
     * The candidate's id, which names the campaign slot rather than the person - the slot
     * {@code SLAYER_CANDIDATE} is Aatrox.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The mayor's own name.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Whether this is a special mayor, one of those standing outside the ordinary rotation.
     */
    @Column(name = "special", nullable = false)
    private boolean special;

    /**
     * The colour the mayor's name is drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.LIGHT_PURPLE;

    /**
     * The perks the mayor brings, every one of which applies for the whole term.
     */
    @Column(name = "perks", nullable = false)
    private @NotNull ConcurrentList<Perk> perks = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Mayor that = (Mayor) o;

        return this.isSpecial() == that.isSpecial()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getPerks(), that.getPerks());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.isSpecial(), this.getFormat(), this.getPerks());
    }

    /**
     * One global effect an elected mayor applies for the whole of their term.
     */
    @Getter
    @GsonType
    public static class Perk {

        /**
         * The perk's id.
         */
        private @NotNull String id = "";

        /**
         * The name the perk is shown under.
         */
        private @NotNull String name = "";

        /**
         * The template line describing the perk, carrying {@code %{VALUE:...}} placeholders that the
         * perk's own stats fill in.
         */
        private @NotNull String description = "";

        /**
         * The stat changes the perk makes, as references to a {@link Stat} by id.
         */
        private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Perk that = (Perk) o;

            return Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getName(), that.getName())
                && Objects.equals(this.getDescription(), that.getDescription())
                && Objects.equals(this.getStats(), that.getStats());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getStats());
        }

    }

    /**
     * A reference to a {@link Stat} by id, together with the single figure a mayor perk renders for
     * it.
     *
     * <p>
     * A mayor perk carries one flat amount rather than a ladder keyed by level or rarity, so this
     * shape holds a lone value and offers no lookup of the stat it names.
     */
    @Getter
    @GsonType
    public static class Substitute {

        /**
         * The id of the {@link Stat} the perk affects.
         */
        private @NotNull String id = "";

        /**
         * Decimal places to render the value to.
         */
        private int precision = 0;

        /**
         * How to decorate the number, which is the prefix and suffix it is drawn with.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Stat.Type type = Stat.Type.NONE;

        /**
         * The colour to draw the value in.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GREEN;

        /**
         * The amount the perk applies - a negative amount paired with {@link Stat.Type#MULTIPLY} is
         * how a discount is spelled.
         */
        private double value;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Substitute that = (Substitute) o;

            return this.getPrecision() == that.getPrecision()
                && this.getValue() == that.getValue()
                && Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getType(), that.getType())
                && Objects.equals(this.getFormat(), that.getFormat());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getPrecision(), this.getType(), this.getFormat(), this.getValue());
        }

    }

}