package dev.sbs.minecraftapi.persistence.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

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
