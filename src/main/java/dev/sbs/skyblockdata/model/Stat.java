package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.renderer.text.ChatColor;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Getter
@Entity
@Table(name = "stats")
public class Stat implements JpaModel {

    public static final double MAGIC_CONSTANT = 719.28;

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "symbol", nullable = false)
    private @NotNull String symbol = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.WHITE;

    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "";

    @Column(name = "base", nullable = false)
    private double base = 0.0;

    @Column(name = "cap", nullable = false)
    private double cap = 0.0;

    @Column(name = "enrichment", nullable = false)
    private double enrichment = 0.0;

    @Column(name = "power_multiplier", nullable = false)
    private double powerMultiplier = 0.0;

    @Column(name = "tuning_multiplier", nullable = false)
    private double tuningMultiplier = 0.0;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @Column(name = "multiplicable", nullable = false)
    private boolean multiplicable;

    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull StatCategory category;

    public double getPowerCoefficient() {
        return (this.getPowerMultiplier() * MAGIC_CONSTANT) / 100.0;
    }

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

    @Getter
    @GsonType
    public static class Substitute {

        private @NotNull String id = "";
        private int precision = 0;
        @Enumerated(EnumType.STRING)
        private @NotNull Type type = Type.NONE;
        @Enumerated(EnumType.STRING)
        private @NotNull ChatColor.Legacy format = ChatColor.Legacy.GREEN;
        private @NotNull ConcurrentMap<Integer, Double> values = Concurrent.newMap();

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

    @Getter
    @RequiredArgsConstructor
    public enum Type {

        NONE("", ""),
        FLAT("+", ""),
        MULTIPLY("", "x"),
        PERCENT("", "%"),
        PLUS_MULTIPLY("+", "x"),
        PLUS_PERCENT("+", "%"),
        SECONDS("", "s");

        private final @NotNull String prefix;
        private final @NotNull String suffix;

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