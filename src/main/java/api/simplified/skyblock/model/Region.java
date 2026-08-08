package api.simplified.skyblock.model;

import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Zones this region contains, owned by {@link Zone#getRegion()} and populated by the provider,
     * which supplies its own list implementation
     */
    @OneToMany(mappedBy = "region")
    private @NotNull List<Zone> zones = new ArrayList<>();

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