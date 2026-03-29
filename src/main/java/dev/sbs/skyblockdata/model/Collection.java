package dev.sbs.minecraftapi.persistence.model;

import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.collection.concurrent.ConcurrentMap;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.api.persistence.type.GsonType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@Getter
@Entity
@Table(name = "collections")
public class Collection implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "items", nullable = false)
    private @NotNull ConcurrentMap<String, Item> items = Concurrent.newMap();

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Collection that = (Collection) o;

        return Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getItems(), that.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getItems());
    }

    @Getter
    @GsonType
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    public static class Item {

        private @NotNull String name;
        private int maxTiers;
        @Getter(AccessLevel.NONE)
        private @NotNull ConcurrentList<Tier> tiers = Concurrent.newList();

        public @NotNull ConcurrentList<Tier> getTiers() {
            return this.tiers;
        }

        public int getMaxRequired() {
            return this.getTiers().get(this.getTiers().size() - 1).getAmountRequired();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Item that = (Item) o;

            return this.getMaxTiers() == that.getMaxTiers()
                && Objects.equals(this.getName(), that.getName())
                && Objects.equals(this.getTiers(), that.getTiers());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getName(), this.getMaxTiers(), this.getTiers());
        }

    }

    @Getter
    @GsonType
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Tier {

        private int tier;
        private int amountRequired;
        private @NotNull ConcurrentList<String> unlocks = Concurrent.newList();

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            Tier that = (Tier) o;

            return this.getTier() == that.getTier()
                && this.getAmountRequired() == that.getAmountRequired()
                && Objects.equals(this.getUnlocks(), that.getUnlocks());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getTier(), this.getAmountRequired(), this.getUnlocks());
        }

    }

}
