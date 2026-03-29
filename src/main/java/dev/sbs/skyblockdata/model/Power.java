package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.api.util.StringUtil;
import dev.sbs.minecraftapi.MinecraftApi;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

@Getter
@Entity
@Table(name = "powers")
public class Power implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "stone_id")
    private @NotNull Optional<String> stoneId = Optional.empty();

    @SerializedName("requiredLevel")
    @Column(name = "required_combat_level", nullable = false)
    private int requiredCombatLevel = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private @NotNull Stage stage = Stage.STARTER;

    @Column(name = "base_values", nullable = false)
    private @NotNull ConcurrentMap<String, Double> baseValues = Concurrent.newMap();

    @Column(name = "bonuses", nullable = false)
    private @NotNull ConcurrentMap<String, Double> bonuses = Concurrent.newMap();

    public @NotNull Optional<Item> getStone() {
        return this.stoneId.flatMap(stoneId -> MinecraftApi.getRepository(Item.class)
            .findFirst(Item::getId, stoneId)
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Power that = (Power) o;

        return this.getRequiredCombatLevel() == that.getRequiredCombatLevel()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getStoneId(), that.getStoneId())
            && Objects.equals(this.getStage(), that.getStage())
            && Objects.equals(this.getBaseValues(), that.getBaseValues())
            && Objects.equals(this.getBonuses(), that.getBonuses());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getStoneId(), this.getRequiredCombatLevel(), this.getStage(), this.getBaseValues(), this.getBonuses());
    }

    public enum Stage {

        STARTER,
        INTERMEDIATE,
        ADVANCED,
        MASTER,
        GRANDIOSE,
        MARVELOUS;

        public @NotNull String getName() {
            return StringUtil.capitalizeFully(this.name().replace("_", " "));
        }

    }

}