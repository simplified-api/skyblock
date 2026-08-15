package api.simplified.skyblock.model;

import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

/**
 * One song on Melody's harp, the rhythm minigame on Melody's Plateau whose completions grant
 * permanent intelligence.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Harp">Harp</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "melody_songs")
public class MelodySong implements JpaModel {

    /**
     * The song's id, matching the key the wire uses under a member's harp progress.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * The song's title.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The tier of the harp menu the song sits in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private @NotNull Difficulty difficulty = Difficulty.EASY;

    /**
     * The permanent intelligence the song grants the first time it is completed, a one-off award
     * rather than a per-play amount.
     */
    @Column(name = "intelligence_reward", nullable = false)
    private int intelligenceReward;

    /**
     * The tiers the harp menu groups its songs into, easiest to hardest.
     */
    public enum Difficulty {

        /**
         * The opening tier, the songs available with no harp progress at all.
         */
        EASY,

        /**
         * The second tier.
         */
        HARD,

        /**
         * The third tier.
         */
        EXPERT,

        /**
         * The fourth tier.
         */
        VIRTUOSO,

        /**
         * The final and hardest tier.
         */
        PRODIGY

    }

}