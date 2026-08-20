package api.simplified.skyblock.model;

import api.simplified.skyblock.SkyBlockData;
import com.google.gson.annotations.SerializedName;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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
@EqualsAndHashCode(useAccessors = true, exclude = "category")
@Table(name = "stats", indexes = @Index(columnList = "name", unique = true))
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
     * Display name of the stat, and the token the reward scrape behind a skill or slayer level
     * matches against.
     *
     * <p>It is the wording the game prints rather than a spelling of {@link #id}, and the two are
     * allowed to differ: {@code WALK_SPEED} prints as {@code Speed} and {@code HUNTER_FORTUNE} as
     * {@code Hunting Fortune}, so deriving one from the other would match nothing on fifty-three
     * reward lines. It is unique because the scrape resolves a line to exactly one stat, and the
     * index says so, so two stats sharing a name fails the generation rather than crediting both.
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
    @ManyToOne(optional = false)
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
    @EqualsAndHashCode(useAccessors = true)
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

    /**
     * The stats a ladder's reward lines grant, read out of the wording the game prints.
     *
     * <p>It sits here because a skill level and a slayer level print the same wording and were
     * scraping it with the same thirty lines each.
     *
     * <p>A line is resolved to a stat rather than every stat being tested against the line. The
     * difference is not only cost: asking each of the 88 stats whether a line ends in its name
     * credits every stat whose name nests inside another, so {@code +0.5% Ability Damage} granted
     * {@code Damage} as well. Resolving the line takes the longest name it spells and stops, which
     * is one grant per line by construction.
     *
     * <p>Each candidate is one probe against the unique index over {@link #getName()}, so the cost
     * is the number of words in the line rather than the size of the table.
     */
    static final class Grants {

        /**
         * The phrase a tiered grant is written with, whose value is the third word rather than the
         * first.
         */
        private static final @NotNull String TIERED = "Grants +";

        /**
         * The arrow a tiered value is written across, whose right-hand side is what the level
         * grants.
         */
        private static final @NotNull String ARROW = "➜";

        private Grants() {
            throw new UnsupportedOperationException("Grants is a static holder");
        }

        /**
         * Sums what a level's reward lines grant, keyed by {@link Stat} id.
         *
         * <p>The match is on the wording the game prints, so an upstream rewording yields nothing
         * rather than failing. Reading it probes the {@link Stat} repository and so needs a
         * connected session.
         *
         * @param unlocks the reward lines, exactly as the menu prints them
         * @return the granted amount per stat id, empty when no line names a stat
         */
        static @NotNull ConcurrentMap<String, Double> of(@NotNull ConcurrentList<String> unlocks) {
            ConcurrentMap<String, Double> granted = Concurrent.newMap();
            int last = unlocks.size() - 1;

            for (int index = 0; index < unlocks.size(); index++) {
                String line = unlocks.get(index);
                String[] words = line.split("\\s+");

                // A flat grant leads with its value and ends in the stat's name.
                if (line.startsWith("+"))
                    credit(granted, endingIn(words), amount(words, 0));

                // A tiered grant names its stat anywhere in the ladder's last line.
                if (index == last && line.contains(TIERED))
                    credit(granted, namedWithin(words), amount(words, 2));
            }

            return granted.toUnmodifiable();
        }

        /**
         * The stat whose name the line ends in, taking the longest where several nest.
         *
         * @param words the line's whitespace-separated words
         * @return the stat named, or {@code null} when none is
         */
        private static @Nullable Stat endingIn(@NotNull String[] words) {
            for (int start = 0; start < words.length; start++) {
                Stat named = named(join(words, start, words.length));

                if (named != null)
                    return named;
            }

            return null;
        }

        /**
         * The stat whose name appears anywhere in the line, taking the longest where several nest.
         *
         * @param words the line's whitespace-separated words
         * @return the stat named, or {@code null} when none is
         */
        private static @Nullable Stat namedWithin(@NotNull String[] words) {
            for (int length = words.length; length > 0; length--) {
                for (int start = 0; start + length <= words.length; start++) {
                    Stat named = named(join(words, start, start + length));

                    if (named != null)
                        return named;
                }
            }

            return null;
        }

        /**
         * The stat carrying one name, in a single probe against the index that declares it unique.
         */
        private static @Nullable Stat named(@NotNull String name) {
            return SkyBlockData.getRepository(Stat.class).findFirstOrNull(Stat::getName, name);
        }

        /**
         * Adds one grant, summing where a level names the same stat twice.
         */
        private static void credit(@NotNull ConcurrentMap<String, Double> granted, @Nullable Stat stat, double amount) {
            if (stat == null || amount <= 0.0)
                return;

            granted.put(stat.getId(), granted.getOrDefault(stat.getId(), 0.0) + amount);
        }

        /**
         * Reads the number out of one word, taking the right-hand side of a tiered pair because that
         * is what the level being reached grants.
         *
         * @param words the line's whitespace-separated words
         * @param position which word carries the value
         * @return the amount granted, or zero when that word carries no number
         */
        private static double amount(@NotNull String[] words, int position) {
            if (position >= words.length)
                return 0.0;

            String value = words[position].replace("+", "").replace("%", "");

            if (value.contains(ARROW))
                value = value.split(ARROW)[1];

            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException unreadable) {
                return 0.0;
            }
        }

        /**
         * Rejoins a run of words as the line spelled them.
         */
        private static @NotNull String join(@NotNull String[] words, int from, int to) {
            return String.join(" ", Arrays.copyOfRange(words, from, to));
        }

    }

}