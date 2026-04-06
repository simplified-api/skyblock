package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.minecraftapi.generator.text.ChatFormat;
import dev.simplified.persistence.JpaModel;
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
