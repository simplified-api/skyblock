package dev.sbs.minecraftapi.persistence.model;

import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
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
@Table(name = "accessories")
public class Accessory implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id;

    @Column(name = "description")
    private @NotNull Optional<String> description = Optional.empty();

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private @NotNull Source source = Source.MISCELLANEOUS;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit", nullable = false)
    private @NotNull Limit limit = Limit.NONE;

    @Getter(AccessLevel.NONE)
    @Column(name = "family")
    private @Nullable Family family;

    @ManyToOne
    @JoinColumn(name = "id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Item item;

    public @NotNull Optional<Family> getFamily() {
        return Optional.ofNullable(this.family);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Accessory that = (Accessory) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getSource(), that.getSource())
            && Objects.equals(this.getLimit(), that.getLimit())
            && Objects.equals(this.getFamily(), that.getFamily());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getDescription(), this.getSource(), this.getLimit(), this.getFamily());
    }

    public enum Limit {

        NONE,
        BINGO,
        RIFT

    }

    public enum Source {

        COLLECTION,
        NPC,
        EVENT,
        MOB_DROP,
        SLAYER,
        QUEST,
        DUNGEON,
        MISCELLANEOUS,
        RIFT

    }

    @Getter
    @GsonType
    public static class Family {

        private @NotNull String id = "";
        private int rank;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Family that = (Family) o;

            return this.getRank() == that.getRank()
                && Objects.equals(this.getId(), that.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getRank());
        }

    }

}
