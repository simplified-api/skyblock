package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.minecraftapi.skyblock.common.Rarity;
import dev.simplified.persistence.JpaModel;
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
@Table(name = "brews")
public class Brew implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false)
    private @NotNull Rarity rarity = Rarity.COMMON;

    @Column(name = "amplified", nullable = false)
    private boolean amplified = false;

    @Column(name = "cost", nullable = false)
    private @NotNull Item.Cost cost = new Item.Cost();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Brew that = (Brew) o;

        return this.isAmplified() == that.isAmplified()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getRarity(), that.getRarity())
            && Objects.equals(this.getCost(), that.getCost());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getRarity(), this.isAmplified(), this.getCost());
    }

}
