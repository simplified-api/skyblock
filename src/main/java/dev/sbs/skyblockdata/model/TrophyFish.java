package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.minecraftapi.skyblock.common.Rarity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "trophy_fishes")
public class TrophyFish implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false)
    private @NotNull Rarity rarity = Rarity.COMMON;

    @SerializedName("zone")
    @Column(name = "zone_id")
    private @NotNull Optional<String> zoneId = Optional.empty();

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "zone_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Zone zone;

    public @NotNull Optional<Zone> getZone() {
        return Optional.ofNullable(this.zone);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        TrophyFish that = (TrophyFish) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getRarity(), that.getRarity())
            && Objects.equals(this.getZoneId(), that.getZoneId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getRarity(), this.getZoneId());
    }

    public enum Tier {

        BRONZE,
        SILVER,
        GOLD,
        DIAMOND

    }

}