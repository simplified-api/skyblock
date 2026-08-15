package api.simplified.skyblock.model;

import api.simplified.skyblock.SkyBlockData;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.EqualsInclude;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.annotations.UtilityClass;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.gson.annotation.SerializedPath;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Everything one named thing contributes to a member's stats, in one place.
 *
 * <p>
 * A row is a source document rather than a single contribution: an identity, a {@link Subject}
 * saying what it attaches to, the tests gating the whole of it, and a list of {@link Rule} each
 * carrying one contribution and whatever extra test it alone needs. The tests are shared because
 * they are shared in the game - a set bonus gates six numbers on one worn count, and written a
 * document per number that count is stated six times.
 *
 * <pre><code>
 * {
 *   "id": "RACING_HELMET_SPEED_CAP",
 *   "name": "Racing Helmet",
 *   "subject": { "kind": "ITEM", "carrierId": "RACING_HELMET" },
 *   "conditions": [ { "input": { "kind": "CARRIER", "carrier": "SLOT" }, "op": "IN", "keys": ["ARMOR"] } ],
 *   "rules": [ { "channels": ["CAP"], "target": { "kind": "STAT", "stat": "WALK_SPEED" },
 *               "value": { "kind": "NUMBER", "amount": 100 } } ]
 * }
 * </code></pre>
 *
 * <p>
 * The grammar lives here rather than beside this class, because every one of its types is a column
 * type on this one and none of them means anything without it. What each part decides:
 *
 * <pre><code>
 *   Subject     what the row attaches to, and the index a lookup finds it through
 *   Condition   the tests, sharing one input vocabulary with the values
 *   Rule        one contribution - which stats, which number of them, what operand
 *   Target      which stats a rule writes
 *   Channel     which number of them
 *   Operation   how the operand folds in
 *   Term        what an operand or a test is written out of
 *   Lookup      a table of numbers read by a computed key
 *   Range       a closed interval, which is what a WITHIN tests against
 *   Validator   what the grammar refuses, checked when the corpus is read
 * </code></pre>
 *
 * <p>
 * {@link #effects} is shorthand for the case nine rows in ten are: one flat addition per stat. It
 * says nothing a rule cannot, and it exists because a reforge grants up to fifteen flat stats and
 * nobody should type fifteen rule objects where fifteen numbers say it. A row carries effects, rules
 * or both, and one carrying neither is only meaningful when it declares a group.
 *
 * <p>
 * {@link #groupId} and {@link #memberIds} declare a group in place. A group exists because something
 * gates on how many of its members are worn, so the row carrying that gate declares the membership
 * and every other row - including one on a different set - names it by id through a count. Membership
 * is authored exactly once, which is the whole point: two lists that must agree with nothing making
 * them agree do not stay agreed.
 *
 * <p>
 * The id is authored and never generated. A generated key on a hand-written corpus makes a defect
 * report uncitable, because there is no stable name for the row that is wrong.
 *
 * <p>
 * There is no association on this table and no foreign id. {@link #subjectCarrierId} holds an item id
 * on one row and a reforge id on the next, so one column cannot join four tables - and a join onto
 * items resolves to null for most rows and, worse, to a real item for any reforge whose id happens to
 * match one. What a join cannot buy, {@link Validator} buys instead, and it reaches further: a
 * target's stat ids and a count's group id are covered too, and no join could have seen either.
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "buffs")
public class Buff implements JpaModel {

    /**
     * The row's own key, unique across the file, and what a defect report names.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the thing contributing, spelled as the wiki spells it.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * What the row attaches to, bound from {@code subject.kind}.
     */
    @SerializedPath("subject.kind")
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_kind", nullable = false)
    private @NotNull Subject.Kind subjectKind = Subject.Kind.PROFILE;

    /**
     * Id of the carrier matched on, bound from {@code subject.carrierId} and absent to match every
     * carrier of the kind.
     */
    @SerializedPath("subject.carrierId")
    @Column(name = "subject_carrier_id")
    private @Nullable String subjectCarrierId;

    /**
     * The second-level name the kind takes, bound from {@code subject.member}.
     */
    @SerializedPath("subject.member")
    @Column(name = "subject_member")
    private @Nullable String subjectMember;

    /**
     * Id of the group this row declares, present only alongside {@link #memberIds}.
     */
    @Column(name = "group_id")
    private @Nullable String groupId;

    /**
     * Item ids making up the declared group.
     */
    @Column(name = "member_ids", nullable = false)
    private @NotNull ConcurrentList<String> memberIds = Concurrent.newUnmodifiableList();

    /**
     * Tests gating the whole row - every one holds, or no rule on it fires.
     */
    @Column(name = "conditions", nullable = false)
    private @NotNull ConcurrentList<Condition> conditions = Concurrent.newUnmodifiableList();

    /**
     * Flat stat additions, keyed by {@link Stat} id, each one shorthand for a rule adding that amount
     * to that stat's value.
     */
    @Column(name = "effects", nullable = false)
    private @NotNull ConcurrentMap<String, Double> effects = Concurrent.newUnmodifiableMap();

    /**
     * The contributions the row makes, one rule each.
     */
    @Column(name = "rules", nullable = false)
    private @NotNull ConcurrentList<Rule> rules = Concurrent.newUnmodifiableList();

    /**
     * What the row attaches to, read back off the three columns it is stored in.
     */
    public @NotNull Subject getSubject() {
        return new Subject(
            this.getSubjectKind(),
            Optional.ofNullable(this.getSubjectCarrierId()),
            Optional.ofNullable(this.getSubjectMember())
        );
    }

    /**
     * Whether the row declares a group other rows may count the worn members of.
     */
    public boolean declaresGroup() {
        return this.getGroupId() != null;
    }

    /**
     * What a row attaches to, and so the index a lookup finds it through.
     *
     * <p>
     * It is a reading of three columns rather than a column of its own, because the subject is what a
     * row is selected by and an index reads columns. The storage is flat and the vocabulary is not:
     * {@link Buff#getSubject()} answers one of these and nothing else has to know the three column
     * names.
     *
     * <p>
     * A row is selected on the triple and never evaluated to find out whether it is relevant, which
     * is the whole reason the subject is not simply another condition.
     *
     * <pre><code>
     * { "kind": "ITEM", "carrierId": "RACING_HELMET" }
     * { "kind": "PET_PERK", "carrierId": "BLACK_CAT", "member": "Hunter" }
     * { "kind": "ITEM" }
     * </code></pre>
     *
     * <p>
     * The third of those is the shape an absent {@link #carrierId} buys: every carrier of that kind,
     * which is what the two generic rarity upgrades are - a recombobulator applies to whatever it was
     * applied to.
     *
     * @param kind which index the row sits in, and so what its carrier is
     * @param carrierId the id matched on, empty to match every carrier of that kind
     * @param member the second-level name, for a kind that has one
     */
    public record Subject(@NotNull Kind kind, @NotNull Optional<String> carrierId, @NotNull Optional<String> member) {

        /**
         * What a row attaches to, and what the word carrier means for it.
         *
         * <p>
         * Every kind is matched against exactly one thing, and the carrier is always the thing the
         * row is attached to in the loadout - so a carrier term means one thing everywhere:
         *
         * <pre><code>
         *   kind         matched against                              the carrier is
         *   ----         ---------------                              --------------
         *   ITEM         an item id the instance is or carries        the item instance
         *   REFORGE      the applied reforge id                       the item instance
         *   ENCHANTMENT  an enchantment id on the instance            the item instance
         *   PET_PERK     the active pet's id, member naming the perk  the pet
         *   PET_ITEM     the item id the active pet holds             the pet
         *   PERK         a levelled node, member naming the tree      the node
         *   GROUP        nothing - a count is what gates it           the member
         *   PROFILE      nothing - always considered                  the member
         * </code></pre>
         */
        public enum Kind {

            /**
             * An item id, whether the instance is that item or carries it applied.
             */
            ITEM,

            /**
             * The reforge applied to an instance.
             */
            REFORGE,

            /**
             * An enchantment on an instance.
             */
            ENCHANTMENT,

            /**
             * A perk of the summoned pet, the second-level name being the perk.
             */
            PET_PERK,

            /**
             * The item the summoned pet is holding.
             */
            PET_ITEM,

            /**
             * A levelled node the member owns, the second-level name being the tree it sits in.
             */
            PERK,

            /**
             * A declared group of items, considered always and gated on how many are worn.
             */
            GROUP,

            /**
             * The member themselves, with no carrier at all - which is what a mayor's perk hangs off.
             */
            PROFILE

        }

    }

    /**
     * One contribution a row makes - which stats, which number of them, what operand, and under what
     * extra tests.
     *
     * <p>
     * A row carries a list of these because a condition set is shared: the deepest specimen in the
     * corpus puts six contributions under one stack of four tests, and written one row per
     * contribution that stack is said six times and an edit to the gate lands in six places.
     * {@link #when} is the escape for the contribution needing one test more than its siblings.
     *
     * <pre><code>
     * { "channels": ["CAP"], "operation": "ADD", "target": { "kind": "STAT", "stat": "WALK_SPEED" },
     *   "value": { "kind": "NUMBER", "amount": 100 } }
     * </code></pre>
     *
     * <p>
     * Every key but the operand has a default, and the defaults are the flat case: add a factor to
     * the bonus half of the carrier's own value. What the four axes decide, and none of them is the
     * others:
     *
     * <pre><code>
     *   channels   which number of the target - its value, its ceiling, or an item's rarity
     *   target     which stats
     *   scope      whose contributions - the carrier's own, or everything
     *   half       which half the running number is read from; a rule always writes the bonus half
     * </code></pre>
     *
     * <p>
     * {@link #ceiling} and {@link #floor} bound <b>this rule's</b> contribution before the operation
     * folds it. They are not the {@link Channel#CAP} channel, which bounds a <b>stat</b> - expressing
     * the first as the second would put one row's private maximum onto every other source of the same
     * stat. Both are nullable so that unbounded and computed-to-zero stay apart.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true, exclude = "stage")
    public static class Rule {

        @Getter(AccessLevel.NONE)
        private @Nullable Stage stage;

        /**
         * Which numbers of the target this operand is written to.
         *
         * <p>
         * It is a list and there is no singular spelling, because a mutually exclusive pair of keys
         * meaning one thing is a union in the reader's head and a nullable column in the model. A
         * list of one is neither.
         */
        private @NotNull ConcurrentList<Channel> channels = Concurrent.newUnmodifiableList(Channel.VALUE);

        /**
         * How the operand folds into the running number.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Operation operation = Operation.ADD;

        /**
         * Which stats the rule writes, absent only on a rule writing nothing but a rarity.
         */
        private @Nullable Target target;

        /**
         * Whose contributions the rule rewrites.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Scope scope = Scope.CARRIER;

        /**
         * Which half of the running number the operand is folded against.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Half half = Half.BONUS;

        /**
         * How the operand is read, which only a scale can answer.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Unit unit = Unit.FACTOR;

        /**
         * The operand, absent only on a rule that removes a channel rather than writing to it.
         */
        private @Nullable Term value;

        /**
         * The most this one rule may contribute, before the operation folds it.
         */
        private @Nullable Double ceiling;

        /**
         * The least this one rule may contribute, which is how a negative operand is bounded.
         */
        private @Nullable Double floor;

        /**
         * Tests gating this rule alone, on top of the ones its row already carries.
         */
        private @NotNull ConcurrentList<Condition> when = Concurrent.newUnmodifiableList();

        /**
         * When the rule folds, taken from the row when it says and from the channels when it does not.
         *
         * <p>
         * A rule writing nothing but a rarity resolves before any stat total exists, so its stage
         * follows from what it writes and stating it would be stating the same fact twice. Everything
         * else runs against the flat totals unless the row asks for {@link Stage#POST} by name.
         *
         * @return the declared stage, or the one the channels imply
         */
        @EqualsInclude
        public @NotNull Stage getStage() {
            if (this.stage != null)
                return this.stage;

            return this.getChannels().stream().allMatch(channel -> channel == Channel.RARITY)
                ? Stage.RARITY
                : Stage.BONUS;
        }

        /**
         * When a rule folds, relative to the totals it reads.
         */
        public enum Stage {

            /**
             * Before any stat total exists, which is the only time an item's rarity can be decided.
             */
            RARITY,

            /**
             * Against the flat totals, once every source has contributed.
             */
            BONUS,

            /**
             * Against the finished totals, after every {@link #BONUS} rule has folded.
             */
            POST

        }

        /**
         * Whose contributions a rule rewrites.
         *
         * <p>
         * It is a declared value because today it is whichever table a caller happened to hand the
         * evaluator, and that is not a property a row can state.
         */
        public enum Scope {

            /**
             * Only what the carrier itself contributed.
             */
            CARRIER,

            /**
             * Everything the member has, from every source and every carrier.
             */
            PROFILE

        }

        /**
         * Which half of a cell a rule reads.
         *
         * <p>
         * A rule always writes the bonus half; this says only which number it reads to compute what
         * it writes. Reading {@link #TOTAL} and writing the bonus half preserves the total a
         * whole-cell rescale produces and differs from it only in the split.
         */
        public enum Half {

            /**
             * What the source itself gave, before any bonus.
             */
            BASE,

            /**
             * What the bonuses have added.
             */
            BONUS,

            /**
             * Both halves together.
             */
            TOTAL

        }

        /**
         * How a scale's operand is read.
         */
        public enum Unit {

            /**
             * The operand is the factor itself, so {@code 0.5} halves.
             */
            FACTOR {

                @Override
                public double toFactor(double operand) {
                    return operand;
                }

            },

            /**
             * The operand is a percentage on top of what is there, so {@code 50} adds half again.
             */
            PERCENT {

                @Override
                public double toFactor(double operand) {
                    return 1 + (operand / 100.0);
                }

            };

            /**
             * Reads the operand as the factor a scale applies.
             *
             * @param operand the number the rule's value evaluated to
             * @return the factor to multiply or divide by
             */
            public abstract double toFactor(double operand);

        }

    }

    /**
     * One test a rule has to pass before it contributes.
     *
     * <p>
     * The whole grammar is one comparison and three combinators. A comparison wraps a {@link Term} -
     * the same vocabulary a value is written out of - and the three combinators nest conditions:
     *
     * <pre><code>
     * { "input": { "kind": "COUNT", "group": "YOUNG_DRAGON" }, "op": "GTE", "amount": 4 }
     * { "input": { "kind": "CARRIER", "carrier": "RARITY" }, "op": "IN", "keys": ["LEGENDARY", "MYTHIC"] }
     * { "not": { "input": { "kind": "CARRIER", "carrier": "ID" }, "op": "EQ", "key": "SNAIL" } }
     * </code></pre>
     *
     * <p>
     * The right-hand side is three separately typed keys - {@link #amount}, {@link #key} and
     * {@link #keys} - and never one slot holding whichever shape arrived. Which one an operator reads
     * follows from the operator, and nothing has to guess from the JSON what was meant.
     *
     * <p>
     * A list of conditions is already a conjunction wherever one appears, so {@link #all} earns its
     * place only inside an {@link #any}.
     *
     * <p>
     * The extension point is the term vocabulary rather than this one: a new family of gate is a new
     * {@link Term.Kind} and nothing here changes.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Condition {

        /**
         * What the comparison reads, absent on a condition that is purely a combinator.
         */
        private @Nullable Term input;

        /**
         * How {@link #input} is compared against the right-hand side.
         */
        @Enumerated(EnumType.STRING)
        private @Nullable Op op;

        /**
         * The number compared against, for an ordering operator or a numeric equality.
         */
        private @Nullable Double amount;

        /**
         * The key compared against, for an equality over something that is not a number.
         */
        private @Nullable String key;

        /**
         * The keys membership is tested against.
         */
        private @NotNull ConcurrentList<String> keys = Concurrent.newUnmodifiableList();

        /**
         * The intervals a {@link Op#WITHIN} tests its input against, holding for any one of them.
         */
        private @NotNull ConcurrentList<Range> ranges = Concurrent.newUnmodifiableList();

        /**
         * Conditions that must all hold, which is only needed inside an {@link #any}.
         */
        private @NotNull ConcurrentList<Condition> all = Concurrent.newUnmodifiableList();

        /**
         * Conditions of which one must hold.
         */
        private @NotNull ConcurrentList<Condition> any = Concurrent.newUnmodifiableList();

        /**
         * A condition that must not hold.
         */
        private @Nullable Condition not;

        /**
         * How a comparison reads its two sides.
         *
         * <p>
         * Which right-hand key each one takes:
         *
         * <pre><code>
         *   op                       reads          input is
         *   --                       -----          --------
         *   GT, GTE, LT, LTE         amount         a number
         *   EQ, NE                   amount or key  either, matching the side given
         *   IN, NOT_IN               keys           a key
         *   WITHIN                   ranges         a number
         * </code></pre>
         */
        public enum Op {

            /**
             * The input equals the right-hand side.
             */
            EQ,

            /**
             * The input differs from the right-hand side.
             */
            NE,

            /**
             * The input is above the amount.
             */
            GT,

            /**
             * The input has reached the amount, equality included.
             */
            GTE,

            /**
             * The input is below the amount.
             */
            LT,

            /**
             * The input is at or below the amount.
             */
            LTE,

            /**
             * The input is one of the keys.
             */
            IN,

            /**
             * The input is none of the keys.
             */
            NOT_IN,

            /**
             * The input falls inside one of the ranges.
             */
            WITHIN

        }

    }

    /**
     * Which stats a rule writes.
     *
     * <p>
     * It never says which number of them - that is the rule's channels - and never whose
     * contributions - that is the rule's scope. Three kinds and two adjustments cover every shape the
     * corpus needs:
     *
     * <pre><code>
     * { "kind": "STAT", "stat": "STRENGTH" }
     * { "kind": "ALL", "except": ["HEALTH"] }
     * { "kind": "CATEGORY", "category": "COMBAT", "plus": ["MAGIC_FIND"] }
     * </code></pre>
     *
     * <p>
     * The set is the kind's, plus {@link #plus}, minus {@link #except}, and an id named by both is
     * excluded. Neither adjustment is a fourth kind because "all but these" and "this category but
     * these" are the same operation over different starting sets - and without them a contribution
     * raising one stat and lowering every other is one rule beside eighty-seven hand-written
     * exceptions.
     *
     * <p>
     * {@link #plus} exists because a stat sits in exactly one category, so a category can name a set
     * no category holds: {@code MAGIC_FIND} is miscellaneous where {@code STRENGTH} and
     * {@code CRIT_DAMAGE} are combat, and a contribution reaching all three is unsayable from
     * {@link Kind#CATEGORY} alone. The alternative spellings both cost more - two rules carrying one
     * authored number is duplication, and an explicit list of every combat stat drifts the day a stat
     * joins the category.
     *
     * <p>
     * A rule writing only an item's rarity carries no target at all. There is one rarity per
     * instance, so a target would have exactly one legal value.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Target {

        /**
         * The starting set of stats, before {@link #plus} and {@link #except} adjust it.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Kind kind = Kind.ALL;

        /**
         * Id of the one {@link Stat} a {@link Kind#STAT} target names.
         */
        private @Nullable String stat;

        /**
         * Id of the {@link StatCategory} a {@link Kind#CATEGORY} target names.
         */
        private @Nullable String category;

        /**
         * Ids of stats the kind selects and the rule does not write.
         */
        private @NotNull ConcurrentList<String> except = Concurrent.newUnmodifiableList();

        /**
         * Ids of stats the rule writes on top of whatever the kind selects.
         */
        private @NotNull ConcurrentList<String> plus = Concurrent.newUnmodifiableList();

        /**
         * Answers whether one stat is in the set this target names.
         *
         * <p>
         * An id named by both {@link #plus} and {@link #except} is excluded, because the adjustments
         * are applied in that order and a subtraction is the last word.
         *
         * @param statModel the stat to test
         * @return {@code true} when the rule writes that stat
         */
        public boolean matches(@NotNull Stat statModel) {
            if (this.getExcept().contains(statModel.getId()))
                return false;

            if (this.getPlus().contains(statModel.getId()))
                return true;

            return switch (this.getKind()) {
                case STAT -> statModel.getId().equals(this.getStat());
                case ALL -> true;
                case CATEGORY -> statModel.getCategoryId().equals(this.getCategory());
            };
        }

        /**
         * How a target names its starting set of stats.
         */
        public enum Kind {

            /**
             * One stat, named by id.
             */
            STAT,

            /**
             * Every stat the computation knows.
             */
            ALL,

            /**
             * Every stat of one {@link StatCategory}, which is what saves a contribution reaching a
             * family from enumerating the family and drifting from it.
             */
            CATEGORY

        }

    }

    /**
     * One input to a rule - the whole of what a value or a test is written out of.
     *
     * <p>
     * A term evaluates to a number or to a key, and the same vocabulary serves both positions because
     * every input a condition wants is an input a value wants. A pet's level gates the Black Cat's
     * perk and also scales it; a group count gates a set bonus and also multiplies a per-piece one.
     * Two vocabularies means one of them is spelled twice and drifts once.
     *
     * <p>
     * Every term is an object carrying a {@link #kind}, and there is no bare-number shorthand
     * anywhere:
     *
     * <pre><code>
     * { "kind": "NUMBER", "amount": 5 }
     * { "kind": "CARRIER", "carrier": "LEVEL" }
     * { "kind": "COUNT", "group": "YOUNG_DRAGON" }
     * { "kind": "MATH", "text": "base + step * level", "inputs": { "base": ..., "step": ..., "level": ... } }
     * </code></pre>
     *
     * <p>
     * The keys are separately declared and typed rather than one slot holding whichever shape
     * arrived, so no column ever binds from a number-or-object union and no adapter has to be written
     * by hand. Most of them are null on any given term, and that width is the price of refusing every
     * union - taken on purpose, because a wide bound class is inspectable and diffable where a narrow
     * one is an untyped payload with better manners.
     *
     * <p>
     * {@link Kind#MATH} names only inputs it declares. Anything in {@link #text} that is not a seeded
     * variable is bound in {@link #inputs} as a term of its own, so an expression naming something
     * nobody supplied is caught when the file is read rather than on the first profile that reaches
     * it. A term's inputs may hold further terms, which is self-reference rather than a union.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Term {

        /**
         * What the term reads from.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Kind kind = Kind.NUMBER;

        /**
         * The literal a {@link Kind#NUMBER} term evaluates to.
         */
        private @Nullable Double amount;

        /**
         * Name of the seeded expression variable a {@link Kind#VARIABLE} term reads.
         */
        private @Nullable String name;

        /**
         * Id of the {@link Stat} a {@link Kind#STAT} term reads, or the literal {@code TARGET} for
         * whichever stat the rule is writing.
         */
        private @Nullable String stat;

        /**
         * Name of the origin a {@link Kind#STAT} term reads, absent to read every origin together.
         */
        private @Nullable String origin;

        /**
         * Which half of the cell a {@link Kind#STAT} term reads.
         */
        @Enumerated(EnumType.STRING)
        private @Nullable Rule.Half half;

        /**
         * Which number of the stat a {@link Kind#STAT} term reads; a rarity is not readable here.
         */
        @Enumerated(EnumType.STRING)
        private @Nullable Channel channel;

        /**
         * Dotted path into the carrier's NBT a {@link Kind#TAG} term reads.
         */
        private @Nullable String path;

        /**
         * How a {@link Kind#TAG} term reads what it found at its path.
         */
        @Enumerated(EnumType.STRING)
        private @Nullable Read read;

        /**
         * The value a {@link Read#COUNT} counts entries equal to, absent to count every entry.
         */
        private @Nullable String matching;

        /**
         * Which property of the carrier a {@link Kind#CARRIER} term reads.
         */
        @Enumerated(EnumType.STRING)
        private @Nullable Carrier carrier;

        /**
         * Which property of the world a {@link Kind#WORLD} term reads.
         */
        @Enumerated(EnumType.STRING)
        private @Nullable World world;

        /**
         * Which property of the live player a {@link Kind#PLAYER} term reads.
         */
        @Enumerated(EnumType.STRING)
        private @Nullable Player player;

        /**
         * Id of the declared group a {@link Kind#COUNT} term counts the worn members of.
         */
        private @Nullable String group;

        /**
         * The table a {@link Kind#LOOKUP} term reads.
         */
        private @Nullable Lookup lookup;

        /**
         * The infix expression a {@link Kind#MATH} term evaluates.
         */
        private @Nullable String text;

        /**
         * One term per free name in {@link #text} that is not a seeded variable.
         */
        private @NotNull ConcurrentMap<String, Term> inputs = Concurrent.newUnmodifiableMap();

        /**
         * Whether the term evaluates to a number rather than to a key.
         *
         * <p>
         * Three kinds answer either way and it is their selector that decides, so this is a property
         * of the whole term rather than of its kind - a carrier's level is a number where a carrier's
         * rarity is a key, and the two are one kind. A term whose selector is missing reads as
         * numeric; the missing selector is itself refused when the file is read.
         *
         * @return {@code true} when the term belongs in an arithmetic position
         */
        public boolean isNumeric() {
            return switch (this.getKind()) {
                case TAG -> this.getRead() == null || this.getRead().isNumeric();
                case CARRIER -> this.getCarrier() == null || this.getCarrier().isNumeric();
                case WORLD -> this.getWorld() == null || this.getWorld().isNumeric();
                default -> true;
            };
        }

        /**
         * Where a term draws its value from, which is the one place this grammar is meant to grow - a
         * new family of gate is a constant here and nothing else.
         */
        public enum Kind {

            /**
             * A literal, read from {@code amount}.
             */
            NUMBER,

            /**
             * The number already standing on the cell being written.
             */
            CURRENT,

            /**
             * One expression variable, seeded before any source contributes.
             */
            VARIABLE,

            /**
             * A stat's running total, or its resolved ceiling.
             */
            STAT,

            /**
             * Something read off the carrier's own NBT.
             */
            TAG,

            /**
             * A property of the thing the row is attached to in the loadout.
             */
            CARRIER,

            /**
             * How many members of a declared group the member is wearing.
             */
            COUNT,

            /**
             * A property of where and when the member is.
             */
            WORLD,

            /**
             * A property of the live player, which nothing on the wire carries.
             *
             * <p>
             * It is here so that a shape with no input is storable, inert and counted rather than
             * absent from the grammar. A term answering nothing makes every condition holding it
             * unresolvable and every rule holding it unevaluated, and the row is reported as inert
             * instead of folded in as a zero. Leaving the kind out is the one option that hides the
             * gap.
             */
            PLAYER,

            /**
             * A number read out of a keyed table.
             */
            LOOKUP,

            /**
             * Arithmetic over other terms.
             */
            MATH

        }

        /**
         * How a {@link Kind#TAG} term reads whatever sits at its path.
         */
        @Getter
        @RequiredArgsConstructor
        public enum Read {

            /**
             * The tag's own numeric value.
             */
            NUMBER(true),

            /**
             * The tag's own string value, as a key.
             */
            TEXT(false),

            /**
             * How many entries the tag holds, optionally only those equal to {@code matching}.
             */
            COUNT(true);

            /**
             * Whether the reading is a number rather than a key.
             */
            private final boolean numeric;

        }

        /**
         * Which property of the carrier a {@link Kind#CARRIER} term reads.
         *
         * <p>
         * The carrier is always the thing the row is attached to in the loadout, so one constant
         * means one thing everywhere: for an applied consumable it is the instance hosting it, for a
         * pet item it is the pet, and for a group or profile row it is the member.
         */
        @Getter
        @RequiredArgsConstructor
        public enum Carrier {

            /**
             * The carrier's own id.
             */
            ID(false),

            /**
             * The carrier's resolved rarity.
             */
            RARITY(false),

            /**
             * Id of the carrier's {@link ItemCategory}.
             */
            CATEGORY(false),

            /**
             * The kind of slot the carrier sits in.
             */
            SLOT(false),

            /**
             * The carrier's level.
             */
            LEVEL(true),

            /**
             * Id of what the carrier is holding.
             */
            HOLDER(false);

            /**
             * Whether the property is a number rather than a key.
             */
            private final boolean numeric;

        }

        /**
         * Which property of where and when the member is a {@link Kind#WORLD} term reads.
         */
        @Getter
        @RequiredArgsConstructor
        public enum World {

            /**
             * Id of the {@link Region} the member is in.
             */
            REGION(false),

            /**
             * Id of the {@link Zone} the member is in.
             */
            ZONE(false),

            /**
             * The hour of the SkyBlock day, which is what a diurnal window is tested against.
             */
            HOUR(true),

            /**
             * The SkyBlock season.
             */
            SEASON(false),

            /**
             * Id of the {@link Event} under way.
             */
            EVENT(false),

            /**
             * The profile's game mode.
             */
            MODE(false),

            /**
             * Id of the {@link Mayor} holding office.
             */
            MAYOR(false),

            /**
             * The dungeon class the member is playing.
             */
            DUNGEON_CLASS(false),

            /**
             * Id of the {@link MobType} being fought.
             */
            MOB_TYPE(false);

            /**
             * Whether the property is a number rather than a key.
             */
            private final boolean numeric;

        }

        /**
         * Which property of the live player a {@link Kind#PLAYER} term reads.
         *
         * <p>
         * Nothing on the wire carries any of these, so every one of them answers nothing today and
         * every rule reading one is inert. They are declared so that a row needing one is storable
         * and counted rather than unsayable.
         */
        public enum Player {

            /**
             * How much of the player's health is left, as a fraction of their maximum.
             */
            HEALTH_FRACTION,

            /**
             * Whether the player is sneaking.
             */
            SNEAKING,

            /**
             * Whether the player is in combat.
             */
            IN_COMBAT,

            /**
             * Whether the player is in water.
             */
            IN_WATER,

            /**
             * The light level where the player is standing.
             */
            LIGHT_LEVEL,

            /**
             * How many entities are near the player.
             */
            NEARBY_ENTITIES,

            /**
             * How many stacks of an ability's own counter the player is holding.
             */
            STACKS

        }

    }

    /**
     * A table of numbers read by one or two computed keys.
     *
     * <p>
     * It is what a ladder is - a pet's stat per rarity, a perk's amount per level, a threshold list a
     * counter climbs:
     *
     * <pre><code>
     * { "on": { "kind": "CARRIER", "carrier": "LEVEL" }, "mode": "THRESHOLD", "amounts": { "1": 5, "50": 10, "100": 20 } }
     * </code></pre>
     *
     * <p>
     * The values are numbers and never terms, and a second axis is a second flat table rather than a
     * table whose entries are tables. A lookup carries {@link #amounts} or {@link #amountsBy2} and
     * never both, and which one it carries follows from whether {@link #on2} is there.
     *
     * <p>
     * {@link #mode} is required and there is no default, because the same shape means opposite things
     * without one - a ladder of three rungs read as a set of bands and the same ladder read as a
     * running sum differ by a factor the author never stated.
     */
    @Getter
    @GsonType
    @EqualsAndHashCode(useAccessors = true)
    public static class Lookup {

        /**
         * What the first axis is keyed by.
         */
        private @Nullable Term on;

        /**
         * What the second axis is keyed by, absent for a one-axis table.
         */
        private @Nullable Term on2;

        /**
         * How a key selects an entry.
         */
        @Enumerated(EnumType.STRING)
        private @NotNull Mode mode = Mode.EXACT;

        /**
         * The one-axis table, keyed by whatever {@link #on} evaluates to.
         */
        private @NotNull ConcurrentMap<String, Double> amounts = Concurrent.newUnmodifiableMap();

        /**
         * The two-axis table, keyed by {@link #on} and then by {@link #on2}.
         */
        private @NotNull ConcurrentMap<String, ConcurrentMap<String, Double>> amountsBy2 = Concurrent.newUnmodifiableMap();

        /**
         * How a computed key selects an entry of the table.
         *
         * <p>
         * Read against one ladder, all four differ:
         *
         * <pre><code>
         *   amounts   { "1": 5, "15": 3, "20": 4 }        key = 17
         *   -------   ----------------------------        --------
         *   EXACT                                         nothing - no entry is keyed 17
         *   THRESHOLD                                     3 - the greatest key reached
         *   THRESHOLD_CUMULATIVE                          8 - every key reached, summed
         *   INTERPOLATE                                   3.4 - between 15 and 20
         * </code></pre>
         *
         * <p>
         * The two threshold readings are two constants because both exist in the corpus and neither
         * is sayable as the other without the author pre-summing the ladder by hand, which encodes a
         * reading where the point is to declare one.
         */
        public enum Mode {

            /**
             * The entry whose key equals the input, and nothing at all when no entry does.
             */
            EXACT,

            /**
             * The entry of the greatest key the input has reached.
             */
            THRESHOLD,

            /**
             * The sum of every entry whose key the input has reached.
             */
            THRESHOLD_CUMULATIVE,

            /**
             * The line between the two nearest keys, flat outside the ends of the table.
             */
            INTERPOLATE

        }

    }

    /**
     * A closed interval of whole numbers, inclusive at both ends.
     *
     * <p>
     * It is what a {@link Condition.Op#WITHIN} tests against, and a condition holds a list of them
     * rather than one so that a window wrapping past the end of its cycle stays sayable:
     *
     * <pre><code>
     * { "op": "WITHIN", "ranges": [{ "low": 20, "high": 23 }, { "low": 0, "high": 5 }] }
     * </code></pre>
     *
     * <p>
     * That is the night hours of a SkyBlock day, which no single low-to-high pair covers. A window
     * needing no wrap is one range and reads as one.
     *
     * @param low the lowest number the range covers
     * @param high the highest number the range covers
     */
    @GsonType
    public record Range(int low, int high) {

        /**
         * Answers whether a number falls inside the range.
         *
         * @param value the number to test
         * @return {@code true} when the number is at or between the two ends
         */
        public boolean contains(int value) {
            return value >= this.low() && value <= this.high();
        }

    }

    /**
     * Which number of a stat a contribution writes.
     *
     * <p>
     * A stat carries more than one number for the same member: what it reads, and the ceiling that
     * reading is used against. A channel says which of them a rule's operand lands on, and it is a
     * list on the rule rather than a single value so that one authored number reaches both:
     *
     * <pre><code>
     * { "channels": ["VALUE", "CAP"], "operation": "ADD", "value": { "kind": "CARRIER", "carrier": "LEVEL" } }
     * </code></pre>
     *
     * <p>
     * The Black Cat's {@code Hunter} perk is that row - its raise scales with the pet's level and
     * applies to the Speed a member has and to the Speed they may have alike. Written as two targets
     * the level is stated twice, and the day one of them changes the other is missed.
     *
     * <p>
     * {@link #RARITY} is the odd one and it is a channel rather than a kind of its own because it is
     * folded by the same operations over the same conditions - a recombobulator adds one and a tier
     * boost adds one, on a number that happens to be a rarity ordinal rather than a stat.
     */
    public enum Channel {

        /**
         * The stat's own number, which is what a member reads.
         */
        VALUE,

        /**
         * The ceiling the stat's value is used against, resolved per member from the stat's base cap.
         */
        CAP,

        /**
         * An item instance's rarity, which resolves before any stat total exists and so may be
         * written only from an operand that reads none.
         */
        RARITY

    }

    /**
     * How one rule's operand folds into the number it is being written onto.
     *
     * <p>
     * Six constants, and the stage a rule runs in is not one of them - which stage reads a rule is a
     * property of the rule rather than of the arithmetic, so it sits in its own column and the same
     * multiply is sayable in either. There are no key prefixes either: an operation is a column, so
     * nothing has to be parsed out of the front of a stat id and no stat id beginning with an
     * operation's letters can be read as one.
     *
     * <p>
     * What each one folds, and what it will not fold onto:
     *
     * <pre><code>
     *   operation   current, operand     channels          skips a stat that forbids a scale
     *   ---------   ----------------     --------          ---------------------------------
     *   ADD         current + operand    all three         no
     *   SUBTRACT    current - operand    all three         no
     *   MULTIPLY    current * operand    VALUE, CAP        yes
     *   DIVIDE      current / operand    VALUE, CAP        yes
     *   SET         operand              all three         no
     *   REMOVE      nothing              CAP               not applicable
     * </code></pre>
     *
     * <p>
     * The guard sits on the proportional pair rather than on the new one. Division by {@code n} is
     * multiplication by {@code 1/n}, so an author wanting to step around a stat's refusal to be
     * scaled would otherwise spell the multiply the other way round, and a guard with a way around it
     * is not a guard. Subtraction is ungated because it is an add of a negation, and neither
     * {@link #SET} nor {@link #REMOVE} is proportional - a stat forbidding a scale does not thereby
     * forbid being assigned.
     *
     * <p>
     * {@link #getRank()} is what orders rules inside one stage, so two rows in either order in the
     * file give the same number: the adds settle, then the scales see the finished sum, then an
     * assignment discards whatever came before it.
     */
    @Getter
    public enum Operation {

        /**
         * Adds the operand to the running number.
         */
        ADD(1, false, false) {

            @Override
            public double apply(double current, double operand) {
                return current + operand;
            }

        },

        /**
         * Takes the operand off the running number.
         *
         * <p>
         * It says nothing an add of a negation could not, and it is here because an operand is not
         * always a literal whose sign an author controls - a lookup and an expression both produce a
         * magnitude.
         */
        SUBTRACT(1, false, false) {

            @Override
            public double apply(double current, double operand) {
                return current - operand;
            }

        },

        /**
         * Scales the running number by the operand, and is skipped for a stat that forbids being
         * scaled.
         */
        MULTIPLY(2, true, true) {

            @Override
            public double apply(double current, double operand) {
                return current * operand;
            }

            /** {@inheritDoc} */
            @Override
            public boolean appliesTo(@NotNull Stat statModel) {
                return statModel.isMultiplicable();
            }

            /** {@inheritDoc} */
            @Override
            public @NotNull ConcurrentList<Channel> getChannels() {
                return PROPORTIONAL_CHANNELS;
            }

        },

        /**
         * Divides the running number by the operand, and is skipped for a stat that forbids being
         * scaled.
         *
         * <p>
         * It is here for the same reason {@link #SUBTRACT} is, plus one of its own: a reciprocal is
         * not always writable exactly, and dividing by three is exact where multiplying by
         * {@code 0.333} is not.
         */
        DIVIDE(2, true, true) {

            @Override
            public double apply(double current, double operand) {
                return current / operand;
            }

            /** {@inheritDoc} */
            @Override
            public boolean appliesTo(@NotNull Stat statModel) {
                return statModel.isMultiplicable();
            }

            /** {@inheritDoc} */
            @Override
            public @NotNull ConcurrentList<Channel> getChannels() {
                return PROPORTIONAL_CHANNELS;
            }

        },

        /**
         * Replaces the running number with the operand.
         */
        SET(3, false, true) {

            @Override
            public double apply(double current, double operand) {
                return operand;
            }

        },

        /**
         * Takes the channel away rather than writing a number to it, so what reads it reads nothing.
         *
         * <p>
         * It carries no operand and folds nothing, which is why {@link #apply} is the identity here -
         * the removal happens where the channel is read, not in an arithmetic step. One mayor perk
         * lifts the Speed ceiling entirely, and that is neither an add of any amount nor an
         * assignment to any number. Saying it as {@code SET 0} would collide with a stat whose
         * ceiling really is zero, because an absent cap is already spelled {@code 0.0} on the stat's
         * own column.
         */
        REMOVE(3, false, false) {

            @Override
            public double apply(double current, double operand) {
                return current;
            }

            /** {@inheritDoc} */
            @Override
            public @NotNull ConcurrentList<Channel> getChannels() {
                return CAP_CHANNEL;
            }

        };

        private static final @NotNull ConcurrentList<Channel> EVERY_CHANNEL = Concurrent.newUnmodifiableList(Channel.values());
        private static final @NotNull ConcurrentList<Channel> PROPORTIONAL_CHANNELS = Concurrent.newUnmodifiableList(Channel.VALUE, Channel.CAP);
        private static final @NotNull ConcurrentList<Channel> CAP_CHANNEL = Concurrent.newUnmodifiableList(Channel.CAP);

        /**
         * Where this operation folds inside one stage - adds first, then scales, then assignments.
         */
        private final int rank;

        /**
         * Whether a rule may say how to read this operation's operand, which only a scale can answer.
         */
        private final boolean unitBearing;

        /**
         * Whether a rule may say which half of a cell this operation reads, which an add cannot use -
         * what an add contributes does not depend on what it is added to.
         */
        private final boolean halfBearing;

        Operation(int rank, boolean unitBearing, boolean halfBearing) {
            this.rank = rank;
            this.unitBearing = unitBearing;
            this.halfBearing = halfBearing;
        }

        /**
         * Folds the operand into the running number.
         *
         * @param current the number as it stands
         * @param operand the number the rule's value evaluated to
         * @return the number to write back
         */
        public abstract double apply(double current, double operand);

        /**
         * Whether this operation may touch a given stat at all.
         *
         * @param statModel the stat being written
         * @return {@code true} unless the stat forbids this operation
         */
        public boolean appliesTo(@NotNull Stat statModel) {
            return true;
        }

        /**
         * The channels this operation may be written to.
         */
        public @NotNull ConcurrentList<Channel> getChannels() {
            return EVERY_CHANNEL;
        }

    }

    /**
     * What the grammar refuses, checked once when the corpus is read rather than when a profile is
     * computed.
     *
     * <p>
     * A grammar that only fails at evaluation fails on somebody's profile, and it fails there as a
     * wrong number rather than as a message. These are the checks that turn a malformed row into a
     * data review. Every refusal names the row and the key.
     *
     * <p>
     * Two entry points, and the split is forced by which vocabulary each refusal needs rather than
     * chosen:
     *
     * <pre><code>
     *   entry point   reaches                          catches
     *   -----------   -------                          -------
     *   shape         one row and nothing else         arity, legality, term position, a zero divisor
     *   corpus        every row, and other tables      a reference resolving against nothing
     * </code></pre>
     *
     * <p>
     * {@link #shape} opens no session and derives nothing - it is a predicate over the object Gson
     * just built, so it may run while a row binds. {@link #corpus} must not be made bind-time: it
     * needs every row of at least one other table, so running it during a bind would resolve a
     * repository from inside the decoder. It runs after the corpus has loaded, over rows already
     * bound, under the session the load already holds.
     *
     * <p>
     * A third set of refusals is not here and cannot be: a variable naming no provider constant, a
     * stat term naming no origin, and an expression naming an input nobody declared are all spelled
     * in a vocabulary this module cannot see, because the dependency runs the other way. Those are
     * checked where those names live.
     */
    @UtilityClass
    public static class Validator {

        /**
         * Checks everything one row can be wrong about on its own.
         *
         * @param row the row to check
         * @return one message per refusal, empty when the row is well formed
         */
        public static @NotNull ConcurrentList<String> shape(@NotNull Buff row) {
            ConcurrentList<String> refusals = Concurrent.newList();

            checkSubject(row, refusals);
            checkGroup(row, refusals);

            if (row.getEffects().isEmpty() && row.getRules().isEmpty() && !row.declaresGroup())
                refusals.add(refusal(row, "rules", "carries no effect, no rule and no group declaration"));

            row.getConditions().forEach(condition -> checkCondition(row, "conditions", condition, refusals));
            row.getRules().forEach(rule -> checkRule(row, rule, refusals));

            return refusals;
        }

        /**
         * Checks every reference the loaded rows make against the tables they name.
         *
         * <p>
         * This is what replaces the foreign key the subject column cannot have, and it reaches
         * further than one would have: a target's stat ids and a count's group id are covered too,
         * and no join could have seen either.
         *
         * @param rows every row of the table, already bound
         * @return one message per refusal, empty when every reference resolves
         */
        public static @NotNull ConcurrentList<String> corpus(@NotNull ConcurrentList<Buff> rows) {
            ConcurrentList<String> refusals = Concurrent.newList();
            ConcurrentSet<String> declaredGroups = Concurrent.newSet();

            for (Buff row : rows) {
                if (!row.declaresGroup())
                    continue;

                if (!declaredGroups.add(row.getGroupId()))
                    refusals.add(refusal(row, "groupId", String.format("declares group '%s', which another row already declares", row.getGroupId())));
            }

            for (Buff row : rows) {
                checkCarrier(row, refusals);

                row.getEffects()
                    .keySet()
                    .stream()
                    .filter(statId -> unresolved(Stat.class, Stat::getId, statId))
                    .forEach(statId -> refusals.add(refusal(row, "effects", String.format("names stat '%s', which no row carries", statId))));

                row.getConditions().forEach(condition -> checkConditionReferences(row, condition, declaredGroups, refusals));

                for (Rule rule : row.getRules()) {
                    checkTargetReferences(row, rule.getTarget(), refusals);
                    rule.getWhen().forEach(condition -> checkConditionReferences(row, condition, declaredGroups, refusals));

                    if (rule.getValue() != null)
                        forEachTerm(rule.getValue(), term -> checkGroupReference(row, term, declaredGroups, refusals));
                }

                checkCompetingAssignments(row, refusals);
            }

            return refusals;
        }

        private static void checkSubject(@NotNull Buff row, @NotNull ConcurrentList<String> refusals) {
            Subject.Kind kind = row.getSubjectKind();
            boolean carrierless = kind == Subject.Kind.GROUP || kind == Subject.Kind.PROFILE;
            boolean twoLevel = kind == Subject.Kind.PET_PERK || kind == Subject.Kind.PERK;

            // a column used off its own kind is how two spellings of one thing start
            if (carrierless && row.getSubjectCarrierId() != null)
                refusals.add(refusal(row, "subject.carrierId", String.format("is set on a '%s' subject, which has no carrier", kind)));

            if (!twoLevel && row.getSubjectMember() != null)
                refusals.add(refusal(row, "subject.member", String.format("is set on a '%s' subject, which has no second level", kind)));
        }

        private static void checkGroup(@NotNull Buff row, @NotNull ConcurrentList<String> refusals) {
            // membership authored twice is membership that drifts, so it is authored once or not at all
            if (!row.getMemberIds().isEmpty() && !row.declaresGroup())
                refusals.add(refusal(row, "memberIds", "names members without declaring a groupId"));

            if (row.declaresGroup() && row.getMemberIds().isEmpty())
                refusals.add(refusal(row, "groupId", String.format("declares group '%s' with no members", row.getGroupId())));
        }

        private static void checkRule(@NotNull Buff row, @NotNull Rule rule, @NotNull ConcurrentList<String> refusals) {
            boolean rarityOnly = rule.getChannels().stream().allMatch(channel -> channel == Channel.RARITY);

            rule.getChannels()
                .stream()
                .filter(channel -> !rule.getOperation().getChannels().contains(channel))
                .forEach(channel -> refusals.add(refusal(row, "rules.channels", String.format("writes '%s' under operation '%s', which cannot reach it", channel, rule.getOperation()))));

            // there is one rarity per instance, so a target on a rarity rule would have one legal value
            if (rarityOnly && rule.getTarget() != null)
                refusals.add(refusal(row, "rules.target", "is set on a rule writing nothing but a rarity"));

            if (!rarityOnly && rule.getTarget() == null)
                refusals.add(refusal(row, "rules.target", "is missing on a rule writing a stat"));

            if (rule.getOperation() == Operation.REMOVE && rule.getValue() != null)
                refusals.add(refusal(row, "rules.value", "is set on a rule that removes a channel rather than writing one"));

            if (rule.getOperation() != Operation.REMOVE && rule.getValue() == null)
                refusals.add(refusal(row, "rules.value", String.format("is missing under operation '%s'", rule.getOperation())));

            // a percentage add has no defined base, and what an add gives does not depend on what it is added to
            if (rule.getUnit() != Rule.Unit.FACTOR && !rule.getOperation().isUnitBearing())
                refusals.add(refusal(row, "rules.unit", String.format("is '%s' under operation '%s', which reads no unit", rule.getUnit(), rule.getOperation())));

            if (rule.getHalf() != Rule.Half.BONUS && !rule.getOperation().isHalfBearing())
                refusals.add(refusal(row, "rules.half", String.format("is '%s' under operation '%s', which reads no half", rule.getHalf(), rule.getOperation())));

            rule.getWhen().forEach(condition -> checkCondition(row, "rules.when", condition, refusals));

            if (rule.getValue() == null)
                return;

            if (!rule.getValue().isNumeric())
                refusals.add(refusal(row, "rules.value", String.format("evaluates to a key under operation '%s', which folds a number", rule.getOperation())));

            // a rarity is resolved before any stat total exists, so nothing that reads one can decide it
            if (rarityOnly) {
                forEachTerm(rule.getValue(), term -> {
                    if (term.getKind() == Term.Kind.STAT || term.getKind() == Term.Kind.CURRENT || term.getKind() == Term.Kind.COUNT)
                        refusals.add(refusal(row, "rules.value", String.format("reads a '%s' term on a rarity rule, which resolves before any total exists", term.getKind())));
                });
            }

            if (rule.getOperation() == Operation.DIVIDE)
                forEachTerm(rule.getValue(), term -> checkDivisor(row, term, refusals));

            forEachTerm(rule.getValue(), term -> checkTerm(row, "rules.value", term, refusals));
        }

        private static void checkCondition(@NotNull Buff row, @NotNull String key, @NotNull Condition condition, @NotNull ConcurrentList<String> refusals) {
            forEachCondition(condition, nested -> {
                if (nested.getInput() == null)
                    return;

                forEachTerm(nested.getInput(), term -> checkTerm(row, key, term, refusals));

                if (nested.getOp() == null)
                    return;

                switch (nested.getOp()) {
                    case IN, NOT_IN -> {
                        if (nested.getInput().isNumeric())
                            refusals.add(refusal(row, key, String.format("tests a number for membership under '%s', which compares keys", nested.getOp())));
                    }
                    case GT, GTE, LT, LTE, WITHIN -> {
                        if (!nested.getInput().isNumeric())
                            refusals.add(refusal(row, key, String.format("orders a key under '%s', which compares numbers", nested.getOp())));
                    }
                    default -> { }
                }
            });
        }

        private static void checkTerm(@NotNull Buff row, @NotNull String key, @NotNull Term term, @NotNull ConcurrentList<String> refusals) {
            // a rarity is not a number of a stat, so it is not readable through a stat term
            if (term.getKind() == Term.Kind.STAT && term.getChannel() == Channel.RARITY)
                refusals.add(refusal(row, key, "reads the rarity channel through a stat term"));

            Lookup lookup = term.getLookup();

            if (term.getKind() != Term.Kind.LOOKUP || lookup == null)
                return;

            // one arity per lookup, and the arity is whether a second axis is there
            boolean twoAxis = lookup.getOn2() != null;
            boolean holdsOne = !lookup.getAmounts().isEmpty();
            boolean holdsTwo = !lookup.getAmountsBy2().isEmpty();

            if (holdsOne == holdsTwo)
                refusals.add(refusal(row, key + ".lookup", "carries both a one-axis and a two-axis table, or neither"));
            else if (twoAxis != holdsTwo)
                refusals.add(refusal(row, key + ".lookup", String.format("declares %s axis and carries the other table", twoAxis ? "a second" : "one")));

            // a ladder of one rung is a condition, spelled as a value
            if (lookup.getMode() != Lookup.Mode.EXACT && holdsOne && lookup.getAmounts().size() == 1)
                refusals.add(refusal(row, key + ".lookup", String.format("is a '%s' ladder of one rung, which is a condition rather than a value", lookup.getMode())));
        }

        private static void checkDivisor(@NotNull Buff row, @NotNull Term term, @NotNull ConcurrentList<String> refusals) {
            // a computed zero divisor leaves the rule unevaluable and counted; a literal one is authored
            if (term.getKind() == Term.Kind.NUMBER && term.getAmount() != null && term.getAmount() == 0.0)
                refusals.add(refusal(row, "rules.value", "divides by a literal zero"));
        }

        private static void checkCarrier(@NotNull Buff row, @NotNull ConcurrentList<String> refusals) {
            String carrierId = row.getSubjectCarrierId();

            if (carrierId == null)
                return;

            boolean resolved = switch (row.getSubjectKind()) {
                case ITEM, PET_ITEM -> !unresolved(Item.class, Item::getId, carrierId);
                case REFORGE -> !unresolved(Reforge.class, Reforge::getId, carrierId);
                case ENCHANTMENT -> !unresolved(Enchantment.class, Enchantment::getId, carrierId);
                case PET_PERK -> !unresolved(Pet.class, Pet::getId, carrierId);
                case PERK -> !unresolved(HotmPerk.class, HotmPerk::getId, carrierId);
                case GROUP, PROFILE -> true;
            };

            if (!resolved)
                refusals.add(refusal(row, "subject.carrierId", String.format("names '%s', which no '%s' row carries", carrierId, row.getSubjectKind())));
        }

        private static void checkTargetReferences(@NotNull Buff row, @Nullable Target target, @NotNull ConcurrentList<String> refusals) {
            if (target == null)
                return;

            ConcurrentList<String> statIds = Concurrent.newList(target.getExcept());
            statIds.addAll(target.getPlus());

            if (target.getStat() != null)
                statIds.add(target.getStat());

            statIds.stream()
                .filter(statId -> unresolved(Stat.class, Stat::getId, statId))
                .forEach(statId -> refusals.add(refusal(row, "rules.target", String.format("names stat '%s', which no row carries", statId))));

            if (target.getCategory() != null && unresolved(StatCategory.class, StatCategory::getId, target.getCategory()))
                refusals.add(refusal(row, "rules.target", String.format("names category '%s', which no row carries", target.getCategory())));
        }

        private static void checkConditionReferences(@NotNull Buff row, @NotNull Condition condition, @NotNull ConcurrentSet<String> declaredGroups, @NotNull ConcurrentList<String> refusals) {
            forEachCondition(condition, nested -> {
                if (nested.getInput() != null)
                    forEachTerm(nested.getInput(), term -> checkGroupReference(row, term, declaredGroups, refusals));
            });
        }

        private static void checkGroupReference(@NotNull Buff row, @NotNull Term term, @NotNull ConcurrentSet<String> declaredGroups, @NotNull ConcurrentList<String> refusals) {
            if (term.getKind() != Term.Kind.COUNT)
                return;

            if (term.getGroup() == null || !declaredGroups.contains(term.getGroup()))
                refusals.add(refusal(row, "group", String.format("counts group '%s', which no row declares", term.getGroup())));
        }

        /**
         * Refuses two assignments that would land on one number with nothing to tell them apart.
         *
         * <p>
         * Only the decidable half is checked: two ungated assignments under one subject, where
         * neither the row nor either rule carries a test, so both provably apply together and the
         * last write wins. Two that are each gated may still overlap on some profile and no reading
         * of the conditions settles it, so that case is left to the author.
         */
        private static void checkCompetingAssignments(@NotNull Buff row, @NotNull ConcurrentList<String> refusals) {
            if (!row.getConditions().isEmpty())
                return;

            ConcurrentSet<String> seen = Concurrent.newSet();

            row.getRules()
                .stream()
                .filter(rule -> rule.getOperation() == Operation.SET)
                .filter(rule -> rule.getWhen().isEmpty())
                .forEach(rule -> rule.getChannels().forEach(channel -> {
                    String slot = String.format("%s/%s", channel, rule.getTarget());

                    if (!seen.add(slot))
                        refusals.add(refusal(row, "rules", String.format("assigns '%s' twice with nothing gating either, so the last one written wins", channel)));
                }));
        }

        private static void forEachTerm(@NotNull Term term, @NotNull Consumer<Term> visitor) {
            visitor.accept(term);
            term.getInputs().forEach((name, input) -> forEachTerm(input, visitor));

            Lookup lookup = term.getLookup();

            if (lookup == null)
                return;

            if (lookup.getOn() != null)
                forEachTerm(lookup.getOn(), visitor);

            if (lookup.getOn2() != null)
                forEachTerm(lookup.getOn2(), visitor);
        }

        private static void forEachCondition(@NotNull Condition condition, @NotNull Consumer<Condition> visitor) {
            visitor.accept(condition);
            condition.getAll().forEach(nested -> forEachCondition(nested, visitor));
            condition.getAny().forEach(nested -> forEachCondition(nested, visitor));

            if (condition.getNot() != null)
                forEachCondition(condition.getNot(), visitor);
        }

        private static <T extends JpaModel> boolean unresolved(@NotNull Class<T> model, @NotNull Function<T, String> idOf, @NotNull String id) {
            return SkyBlockData.getRepository(model).findFirst(idOf, id).isEmpty();
        }

        private static @NotNull String refusal(@NotNull Buff row, @NotNull String key, @NotNull String problem) {
            return String.format("buff '%s': %s %s", row.getId(), key, problem);
        }

    }

}
