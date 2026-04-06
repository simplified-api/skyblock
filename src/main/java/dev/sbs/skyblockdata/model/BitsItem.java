package dev.sbs.minecraftapi.persistence.model;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.type.GsonType;
import dev.simplified.util.StringUtil;
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
@Table(name = "bits_items")
public class BitsItem implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private @NotNull Type type = Type.ITEM;

    @Column(name = "cost", nullable = false)
    private int cost;

    @Column(name = "variants", nullable = false)
    private @NotNull ConcurrentList<Variant> variants = Concurrent.newList();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        BitsItem that = (BitsItem) o;

        return this.getCost() == that.getCost()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getType(), that.getType())
            && Objects.equals(this.getVariants(), that.getVariants());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getType(), this.getCost(), this.getVariants());
    }

    public enum Type {

        ACCESSORY,
        ATTRIBUTE_SHARD,
        CONSUMABLE,
        COSMETIC,
        DYE,
        ENCHANTED_BOOK,
        ENRICHMENT,
        ITEM,
        SACK,
        UPGRADE;

        public @NotNull String getName() {
            return StringUtil.capitalizeFully(this.name().replace("_", " "));
        }

    }

    @Getter
    @GsonType
    public static class Variant {

        private @NotNull String id = "";
        private int cost;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Variant that = (Variant) o;

            return this.getCost() == that.getCost()
                && Objects.equals(this.getId(), that.getId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getId(), this.getCost());
        }

    }

}
