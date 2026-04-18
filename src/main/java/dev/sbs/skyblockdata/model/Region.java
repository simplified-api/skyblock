package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.renderer.text.ChatColor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "regions")
public class Region implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GRAY;

    @Column(name = "game_type", nullable = false)
    private @NotNull String gameType = "";

    @Column(name = "mode", nullable = false)
    private @NotNull String mode = "";

    @OneToMany(mappedBy = "region")
    private @NotNull ConcurrentList<Zone> zones = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Region that = (Region) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getGameType(), that.getGameType())
            && Objects.equals(this.getMode(), that.getMode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getFormat(), this.getGameType(), this.getMode());
    }

}