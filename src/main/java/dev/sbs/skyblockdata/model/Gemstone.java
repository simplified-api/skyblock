package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.minecraftapi.skyblock.common.Rarity;
import dev.sbs.renderer.text.ChatColor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "gemstones")
public class Gemstone implements JpaModel {

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

    @SerializedName("stat")
    @Column(name = "stat_id", nullable = false)
    private @NotNull String statId = "";

    @Column(name = "values", nullable = false)
    private @NotNull ConcurrentMap<Type, ConcurrentMap<Rarity, Double>> values = Concurrent.newMap();

    @ManyToOne
    @JoinColumn(name = "stat_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Stat stat;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Gemstone that = (Gemstone) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getSymbol(), that.getSymbol())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getStatId(), that.getStatId())
            && Objects.equals(this.getValues(), that.getValues());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getSymbol(), this.getFormat(), this.getStatId(), this.getValues());
    }

    public enum Type {

        ROUGH,
        FLAWED,
        FINE,
        FLAWLESS,
        PERFECT

    }

}