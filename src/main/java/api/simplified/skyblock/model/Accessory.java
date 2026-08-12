package api.simplified.skyblock.model;

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

/**
 * The catalogue entry for one accessory - a talisman, ring or artifact that grants its buff merely
 * by sitting in the inventory or the Accessory Bag.
 *
 * <p>
 * The row decorates the {@link Item} of the same id, adding the metadata the item table does not
 * carry: where the accessory comes from and which upgrade family it sits on.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Accessories">Accessories</a>
 */
@Getter
@Entity
@Table(name = "accessories")
public class Accessory implements JpaModel {

    /**
     * The item id, and simultaneously the join key onto the {@link Item} row of the same name.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id;

    /**
     * Template text for the tooltip, carrying {@code %{KEYWORD:...}} and {@code %{VALUE:...}}
     * placeholders; absent for most accessories.
     */
    @Column(name = "description")
    private @NotNull Optional<String> description = Optional.empty();

    /**
     * How the accessory is obtained, for grouping a checklist by acquisition route.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private @NotNull Source source = Source.MISCELLANEOUS;

    /**
     * Which game mode the accessory is confined to, so a completion count can exclude the
     * accessories a profile could never own.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "limit", nullable = false)
    private @NotNull Limit limit = Limit.NONE;

    @Getter(AccessLevel.NONE)
    @Column(name = "family")
    private @Nullable Family family;

    /**
     * The resolved {@link Item} this accessory decorates, joined on the shared id.
     */
    @ManyToOne
    @JoinColumn(name = "id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull Item item;

    /**
     * The upgrade ladder this accessory sits on, empty for one that stands alone.
     */
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

    /**
     * The kind of profile an accessory is restricted to.
     */
    public enum Limit {

        /**
         * Usable on any profile.
         */
        NONE,

        /**
         * Only exists on a Bingo profile.
         */
        BINGO,

        /**
         * Only exists inside the Rift.
         */
        RIFT

    }

    /**
     * The route by which an accessory is obtained.
     */
    public enum Source {

        /**
         * Unlocked by a collection tier.
         */
        COLLECTION,

        /**
         * Bought from an NPC shop.
         */
        NPC,

        /**
         * Awarded by a seasonal event.
         */
        EVENT,

        /**
         * Dropped by a mob.
         */
        MOB_DROP,

        /**
         * Dropped by a slayer boss.
         */
        SLAYER,

        /**
         * Given as a quest reward.
         */
        QUEST,

        /**
         * Found in Catacombs reward chests.
         */
        DUNGEON,

        /**
         * Anything with no single route, and the default.
         */
        MISCELLANEOUS,

        /**
         * Obtained inside the Rift.
         */
        RIFT

    }

    /**
     * An accessory's place on an upgrade ladder, where each rung is a stronger spelling of the
     * accessory below it.
     */
    @Getter
    @GsonType
    public static class Family {

        /**
         * The ladder's name, shared by every rung of it.
         */
        private @NotNull String id = "";

        /**
         * This accessory's position on the ladder, a higher rank superseding a lower one - only the
         * highest owned rank of a family counts toward magical power.
         */
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
