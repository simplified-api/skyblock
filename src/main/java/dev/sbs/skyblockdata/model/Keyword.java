package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.renderer.text.ChatColor;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "keywords")
public class Keyword implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "plural")
    private @NotNull Optional<String> plural = Optional.empty();

    @Column(name = "symbol")
    private @NotNull Optional<String> symbol = Optional.empty();

    @Column(name = "format")
    private @NotNull Optional<ChatColor.Legacy> format = Optional.empty();

    public boolean hasPlural() {
        return this.getPlural().isPresent();
    }

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