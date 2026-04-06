package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "fairy_souls")
public class FairySoul implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private int id = 0;

    @Column(name = "x", nullable = false)
    private double x = 0;

    @Column(name = "y", nullable = false)
    private double y = 0;

    @Column(name = "z", nullable = false)
    private double z = 0;

    @Column(name = "walkable", nullable = false)
    private boolean walkable = false;

    @SerializedName("zone")
    @Column(name = "zone_id", nullable = false)
    private @NotNull String zoneId = "";

    @ManyToOne
    @JoinColumn(name = "zone_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Zone zone;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        FairySoul that = (FairySoul) o;

        return this.getX() == that.getX()
            && this.getY() == that.getY()
            && this.getZ() == that.getZ()
            && this.isWalkable() == that.isWalkable()
            && Objects.equals(this.getZoneId(), that.getZoneId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getX(), this.getY(), this.getZ(), this.isWalkable(), this.getZoneId());
    }

}