package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.persistence.JpaModel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@Getter
@Entity
@Table(name = "pet_items")
public class PetItem implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "percentage", nullable = false)
    private boolean percentage;

    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    public boolean notPercentage() {
        return !this.isPercentage();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        PetItem that = (PetItem) o;

        return this.isPercentage() == that.isPercentage()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getStats(), that.getStats());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.isPercentage(), this.getStats());
    }

}
