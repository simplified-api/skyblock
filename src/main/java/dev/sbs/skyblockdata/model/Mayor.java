package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.renderer.text.ChatFormat;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "mayors")
public class Mayor implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "special", nullable = false)
    private boolean special;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatFormat format = ChatFormat.LIGHT_PURPLE;

    @Column(name = "perks", nullable = false)
    private @NotNull ConcurrentList<Perk> perks = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Mayor that = (Mayor) o;

        return this.isSpecial() == that.isSpecial()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getPerks(), that.getPerks());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.isSpecial(), this.getFormat(), this.getPerks());
    }

    @Getter
    @GsonType
    public static class Perk {

        private @NotNull String id = "";
        private @NotNull String name = "";
        private @NotNull String description = "";
        private @NotNull ConcurrentList<Substitute> stats = Concurrent.newList();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Perk that = (Perk) o;

            return Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getName(), that.getName())
                && Objects.equals(this.getDescription(), that.getDescription())
                && Objects.equals(this.getStats(), that.getStats());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getStats());
        }

    }

    @Getter
    @GsonType
    public static class Substitute {

        private @NotNull String id = "";
        private int precision = 0;
        @Enumerated(EnumType.STRING)
        private @NotNull Stat.Type type = Stat.Type.NONE;
        @Enumerated(EnumType.STRING)
        private @NotNull ChatFormat format = ChatFormat.GREEN;
        private double value;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Substitute that = (Substitute) o;

            return this.getPrecision() == that.getPrecision()
                && this.getValue() == that.getValue()
                && Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getType(), that.getType())
                && Objects.equals(this.getFormat(), that.getFormat());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getPrecision(), this.getType(), this.getFormat(), this.getValue());
        }

    }

}