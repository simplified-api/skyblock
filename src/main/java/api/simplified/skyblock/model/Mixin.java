package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.ForeignIds;
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

/**
 * A mixin - a rare or legendary consumable brewed into a God Potion, or drunk on its own, to add one
 * more effect for the potion's duration.
 *
 * <p>
 * Some mixins only take effect in particular regions, which is what {@code regionIds} narrows.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Mixins">Mixins</a>
 */
@Getter
@Entity
@Table(name = "mixins")
public class Mixin implements JpaModel {

    /**
     * The mixin's item id, and the key it joins on - a mixin row decorates the {@link Item} row of
     * the same name.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The name the mixin is shown under.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The stored ids of the regions the mixin's effect applies in; an empty list means everywhere.
     */
    @SerializedName("regions")
    @Column(name = "regions", nullable = false)
    private @NotNull ConcurrentList<String> regionIds = Concurrent.newList();

    /**
     * The resolved {@link Item}, read from the same {@code id} column the mixin is keyed by.
     */
    @ManyToOne
    @JoinColumn(name = "id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Item item;

    /**
     * The resolved {@link Region} rows, filled in from {@code regionIds} rather than bound.
     */
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