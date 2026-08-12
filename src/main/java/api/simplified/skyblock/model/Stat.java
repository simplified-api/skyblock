package api.simplified.skyblock.model;

import api.simplified.skyblock.SkyBlockData;
import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
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
import lib.minecraft.text.ChatColor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * A stat - one of the named values a member or a mob carries, such as health, strength or mining
 * fortune. The row is the stat's definition: the value everyone starts with, how it is displayed,
 * and the multipliers that govern how magical power and tuning scale it.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Stats">Stats</a>
 */
@Getter
@Entity
@Table(name = "stats")
public class Stat implements JpaModel {

    /**
     * The community-derived constant of the magical power curve.
     */
    public static final double MAGIC_CONSTANT = 719.28;

    /**
     * The stat's id, the key every substitute, effects map and bonus payload names it by.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the stat, and the token the tooltip scrape behind a skill or slayer level
     * matches against.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The glyph drawn beside the stat's name.
     */
    @Column(name = "symbol", nullable = false)
    private @NotNull String symbol = "";

    /**
     * The colour the stat's name and glyph are drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.WHITE;

    /**
     * Id of the owning {@link StatCategory}, bound from the wire key {@code category}.
     */
    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "";

    /**
     * The value every member carries before any bonus is added.
     */
    @Column(name = "base", nullable = false)
    private double base = 0.0;

    /**
     * The ceiling the stat is clamped to, {@code 0.0} meaning it is uncapped.
     */
    @Column(name = "cap", nullable = false)
    private double cap = 0.0;

    /**
     * The amount one accessory enrichment of this stat grants, {@code 0.0} where the stat cannot be
     * enriched.
     */
    @Column(name = "enrichment", nullable = false)
    private double enrichment = 0.0;

    /**
     * How strongly an accessory bag power's grant of this stat scales with magical power, {@code 0.0}
     * where the stat does not scale at all.
     */
    @Column(name = "power_multiplier", nullable = false)
    private double powerMultiplier = 0.0;

    /**
     * How far one tuning point moves the stat, {@code 0.0} where the stat cannot be tuned.
     */
    @Column(name = "tuning_multiplier", nullable = false)
    private double tuningMultiplier = 0.0;

    /**
     * Whether the stat is shown in a member's stat menu rather than only used internally.
     */
    @Column(name = "visible", nullable = false)
    private boolean visible;

    /**
     * Whether multiplicative bonuses apply to the stat, as opposed to flat additions only.
     */
    @Column(name = "multiplicable", nullable = false)
    private boolean multiplicable;

    /**
     * The {@link StatCategory} row behind {@link #categoryId}, resolved on the same column.
     */
    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull StatCategory category;

    /**
     * The per-power coefficient an accessory bag calculation multiplies by - the stat's power
     * multiplier taken against {@link #MAGIC_CONSTANT} and expressed per hundred magical power.
     */
    public double getPowerCoefficient() {
        return (this.getPowerMultiplier() * MAGIC_CONSTANT) / 100.0;
    }

    /**
     * Negates the {@code visible} flag.
     *
     * @return {@code true} when the stat is internal and never shown in the stat menu
     */
    public boolean notVisible() {
        return !this.isVisible();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Stat that = (Stat) o;

        return this.getBase() == that.getBase()
            && this.getCap() == that.getCap()
            && this.getEnrichment() == that.getEnrichment()
            && this.getPowerMultiplier() == that.getPowerMultiplier()
            && this.getTuningMultiplier() == that.getTuningMultiplier()
            && this.isVisible() == that.isVisible()
            && this.isMultiplicable() == that.isMultiplicable()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getSymbol(), that.getSymbol())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getCategoryId(), that.getCategoryId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getSymbol(), this.getFormat(), this.getCategoryId(), this.getBase(), this.getCap(), this.getEnrichment(), this.getPowerMultiplier(), this.getTuningMultiplier(), this.isVisible(), this.isMultiplicable());
    }

    /**
     * A reference to a {@link Stat} by id, plus the amounts to render for it and how to render them.
     *
     * <p>
     * It is the shape the reference rows share - an enchantment, a Heart of the Mountain perk, a pet
     * item, a potion effect and an Essence Shop perk each hold a list of these rather than a resolved
     * stat.
     */
    @Getter
    @GsonType
    public static class Substitute {

        /**
         * Id of the {@link Stat} being granted.
         */
        private @NotNull String id = "";

        /**
         * Decimal places to render the amount to.
         */
        private int precision = 0;

        /**
         * How to prefix and suffix the rendered amount.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Type type = Type.NONE;

        /**
         * The colour to render the amount in.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GREEN;

        /**
         * The amount granted, keyed by the level or the tier that grants it.
         */
        private @NotNull ConcurrentMap<Integer, Double> values = Concurrent.newMap();

        /**
         * The {@link Stat} this substitute names, resolved through {@link SkyBlockData#getRepository}
         * and so requiring a connected session. It is empty for a blank id rather than throwing.
         */
        public @NotNull Optional<Stat> getStat() {
            if (this.id.isEmpty())
                return Optional.empty();
            return SkyBlockData.getRepository(Stat.class).findFirst(Stat::getId, this.id);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Substitute that = (Substitute) o;

            return this.getPrecision() == that.getPrecision()
                && Objects.equals(this.getId(), that.getId())
                && Objects.equals(this.getType(), that.getType())
                && Objects.equals(this.getFormat(), that.getFormat())
                && Objects.equals(this.getValues(), that.getValues());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getPrecision(), this.getType(), this.getFormat(), this.getValues());
        }

    }

    /**
     * How a substituted stat amount is decorated when it is rendered.
     */
    @Getter
    @RequiredArgsConstructor
    public enum Type {

        /**
         * No decoration at all, and the default.
         */
        NONE("", ""),

        /**
         * A flat addition, rendered {@code +n}.
         */
        FLAT("+", ""),

        /**
         * A multiplier, rendered {@code nx}.
         */
        MULTIPLY("", "x"),

        /**
         * A percentage, rendered {@code n%}.
         */
        PERCENT("", "%"),

        /**
         * An added multiplier, rendered {@code +nx}.
         */
        PLUS_MULTIPLY("+", "x"),

        /**
         * An added percentage, rendered {@code +n%}.
         */
        PLUS_PERCENT("+", "%"),

        /**
         * A duration, rendered {@code ns}.
         */
        SECONDS("", "s");

        /**
         * The text drawn in front of the amount.
         */
        private final @NotNull String prefix;

        /**
         * The text drawn after the amount.
         */
        private final @NotNull String suffix;

        /**
         * Renders a rarity-keyed pet value at a given level, decorated for this type.
         *
         * @param level the level to evaluate the value at
         * @param value the base amount and the per-level scalar to evaluate
         * @return the base plus the scalar taken level times, between this type's prefix and suffix
         */
        public @NotNull String format(int level, @NotNull Pet.Substitute.Value value) {
            return String.format(
                "%s%s%s",
                this.getPrefix(),
                value.getBase() + (level * value.getScalar()),
                this.getSuffix()
            );
        }

    }

}