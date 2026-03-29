package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.api.persistence.type.GsonType;
import dev.sbs.api.util.StringUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
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
