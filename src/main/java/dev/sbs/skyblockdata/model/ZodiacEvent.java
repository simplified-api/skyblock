package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.api.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "zodiac_events")
public class ZodiacEvent implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    public int getRecurringYear() {
        return this.releaseYear % 12;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        ZodiacEvent that = (ZodiacEvent) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && this.getReleaseYear() == that.getReleaseYear();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getReleaseYear());
    }

}