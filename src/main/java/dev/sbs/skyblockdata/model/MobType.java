package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.renderer.text.ChatColor;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "mob_types")
public class MobType implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "symbol", nullable = false)
    private @NotNull String symbol = "";

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