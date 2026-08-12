package api.simplified.skyblock.model;

import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * The display vocabulary for one named game term - how it is spelled, pluralised, coloured and
 * symbolled wherever a description substitutes it in.
 *
 * <p>
 * A description is a template rather than prose, writing the term as {@code %{KEYWORD:ID}}; this
 * row is what fills that token in, and so is what makes an accessory, a mayor perk or an essence
 * shop perk renderable.
 */
@Getter
@Entity
@Table(name = "keywords")
public class Keyword implements JpaModel {

    /**
     * The term's id, the token a description writes as {@code %{KEYWORD:ID}}.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The singular spelling of the term.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The plural spelling, absent where the term does not pluralise.
     */
    @Column(name = "plural")
    private @NotNull Optional<String> plural = Optional.empty();

    /**
     * The glyph drawn with the term, absent for most terms.
     */
    @Column(name = "symbol")
    private @NotNull Optional<String> symbol = Optional.empty();

    /**
     * The colour to draw the term in, absent where the surrounding text decides.
     */
    @Column(name = "format")
    private @NotNull Optional<ChatColor.Legacy> format = Optional.empty();

    /**
     * Whether a plural spelling is present - a blank spelling counts as present, which many rows
     * carry.
     */
    public boolean hasPlural() {
        return this.getPlural().isPresent();
    }

    /**
     * Whether a symbol is present - a blank symbol counts as present, which many rows carry.
     */
    public boolean hasSymbol() {
        return this.getSymbol().isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Keyword that = (Keyword) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getPlural(), that.getPlural())
            && Objects.equals(this.getSymbol(), that.getSymbol())
            && Objects.equals(this.getFormat(), that.getFormat());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getPlural(), this.getSymbol(), this.getFormat());
    }

}