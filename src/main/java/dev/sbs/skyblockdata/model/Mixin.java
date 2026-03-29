package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.persistence.ForeignIds;
import dev.sbs.api.persistence.JpaModel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Objects;

@Getter
@Entity
@Table(name = "mixins")
public class Mixin implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @SerializedName("regions")
    @Column(name = "regions", nullable = false)
    private @NotNull ConcurrentList<String> regionIds = Concurrent.newList();

    @ManyToOne
    @JoinColumn(name = "id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Item item;

    @ForeignIds("regionIds")
    private transient @NotNull ConcurrentList<Region> regions = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Mixin that = (Mixin) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getRegionIds(), that.getRegionIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getRegionIds());
    }

}