package api.simplified.skyblock.model;

import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import dev.simplified.util.StringUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * An accessory bag power - the bag-wide stat profile selected at Maxwell in the Thaumaturgist, whose
 * grant scales with the member's magical power. A power stands in place of reforging the accessories
 * themselves.
 *
 * <p>
 * "No Power" is a row like any other, so a member who has selected nothing still resolves to a power
 * rather than to nothing at all.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Powers">Powers</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true, exclude = "stone")
@Table(name = "powers")
public class Power implements JpaModel {

    /**
     * The power's id, matching the value the wire stores as the member's selected power.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the power.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * Id of the power stone that unlocks the power, absent for the powers unlocked by other means.
     */
    @Column(name = "stone_id")
    private @NotNull Optional<String> stoneId = Optional.empty();

    /**
     * Combat level a member needs before the power can be selected, bound from the wire key
     * {@code requiredLevel}.
     */
    @SerializedName("requiredLevel")
    @Column(name = "required_combat_level", nullable = false)
    private int requiredCombatLevel = 0;

    /**
     * How far into the game the power is meant to be used, which is what orders the selection menu.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private @NotNull Stage stage = Stage.STARTER;

    /**
     * The stats the power grants at the reference magical power, keyed by {@link Stat} id. These are
     * the amounts a stat's power multiplier scales.
     */
    @Column(name = "base_values", nullable = false)
    private @NotNull ConcurrentMap<String, Double> baseValues = Concurrent.newMap();

    /**
     * Additional flat stats the power grants outright, keyed by {@link Stat} id and scaled by nothing.
     */
    @Column(name = "bonuses", nullable = false)
    private @NotNull ConcurrentMap<String, Double> bonuses = Concurrent.newMap();

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "stone_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable Item stone;

    /**
     * The {@link Item} row the power's stone id names, resolved on the same column and empty when the
     * power has no stone.
     */
    public @NotNull Optional<Item> getStone() {
        return Optional.ofNullable(this.stone);
    }

    /**
     * How far into a profile's progression a power is meant to be used, earliest first.
     */
    public enum Stage {

        /**
         * The earliest powers, and the default for a row that names no stage.
         */
        STARTER,

        /**
         * The stage past a profile's opening.
         */
        INTERMEDIATE,

        /**
         * The stage above intermediate.
         */
        ADVANCED,

        /**
         * The stage a well-progressed profile selects from.
         */
        MASTER,

        /**
         * The stage above master.
         */
        GRANDIOSE,

        /**
         * The furthest stage, for the powers meant for an all but finished profile.
         */
        MARVELOUS;

        /**
         * The stage's name in title case, with underscores rendered as spaces.
         */
        public @NotNull String getName() {
            return StringUtil.capitalizeFully(this.name().replace("_", " "));
        }

    }

}