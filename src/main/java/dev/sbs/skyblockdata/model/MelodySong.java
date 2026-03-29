package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.api.persistence.JpaModel;
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
@Table(name = "melody_songs")
public class MelodySong implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private @NotNull Difficulty difficulty = Difficulty.EASY;

    @Column(name = "intelligence_reward", nullable = false)
    private int intelligenceReward;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        MelodySong that = (MelodySong) o;

        return this.getIntelligenceReward() == that.getIntelligenceReward()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDifficulty(), that.getDifficulty());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDifficulty(), this.getIntelligenceReward());
    }

    public enum Difficulty {

        EASY,
        HARD,
        EXPERT,
        VIRTUOSO,
        PRODIGY

    }

}