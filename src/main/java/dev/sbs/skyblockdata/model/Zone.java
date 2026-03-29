package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.minecraftapi.render.text.ChatFormat;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Objects;

@Getter
@Entity
@Table(name = "zones")
public class Zone implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatFormat format = ChatFormat.GRAY;

    @SerializedName("region")
    @Column(name = "region_id", nullable = false)
    private @NotNull String regionId = "";

    @ManyToOne
    @JoinColumn(name = "region_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Region region;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Zone that = (Zone) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getRegionId(), that.getRegionId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getFormat(), this.getRegionId());
    }

}