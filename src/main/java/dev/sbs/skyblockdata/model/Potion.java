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
@Table(name = "potions")
public class Potion implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    @Column(name = "buff", nullable = false)
    private boolean buff;

    @Column(name = "brewable", nullable = false)
    private boolean brewable;

    @Column(name = "stats", nullable = false)
    private @NotNull ConcurrentList<Stat.Substitute> stats = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Potion that = (Potion) o;

        return this.isBuff() == that.isBuff()
            && this.isBrewable() == that.isBrewable()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getStats(), that.getStats());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.isBuff(), this.isBrewable(), this.getStats());
    }

}