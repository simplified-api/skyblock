package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.minecraftapi.render.text.ChatFormat;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "bestiary_categories")
public class BestiaryCategory implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @SerializedName("region")
    @Column(name = "region_id")
    private @NotNull Optional<String> regionId = Optional.empty();

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatFormat format = ChatFormat.GREEN;

    @Column(name = "ordinal", nullable = false)
    private int ordinal = -1;

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "region_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Region region;

    public @NotNull Optional<Region> getRegion() {
        return Optional.ofNullable(this.region);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        BestiaryCategory that = (BestiaryCategory) o;

        return this.getOrdinal() == that.getOrdinal()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getRegionId(), that.getRegionId())
            && Objects.equals(this.getFormat(), that.getFormat());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getRegionId(), this.getFormat(), this.getOrdinal());
    }

}
