package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.renderer.text.ChatFormat;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.ForeignIds;
import dev.simplified.persistence.JpaModel;
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

@Getter
@Entity
@Table(name = "bestiary_families")
public class BestiaryFamily implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Column(name = "description", nullable = false)
    private @NotNull String description = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatFormat format = ChatFormat.GREEN;

    @Column(name = "bracket", nullable = false)
    private int bracket = 1;

    @Column(name = "max_tier", nullable = false)
    private int maxTier = 25;

    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "";

    @SerializedName("subcategory")
    @Column(name = "subcategory_id")
    private @NotNull Optional<String> subcategoryId = Optional.empty();

    @SerializedName("mobTypes")
    @Column(name = "mob_types", nullable = false)
    private @NotNull ConcurrentList<String> mobTypeIds = Concurrent.newUnmodifiableList();

    @Column(name = "mobs", nullable = false)
    private @NotNull ConcurrentList<String> mobs = Concurrent.newUnmodifiableList();

    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull BestiaryCategory category;

    @ManyToOne
    @Getter(AccessLevel.NONE)
    @JoinColumn(name = "subcategory_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @Nullable BestiarySubcategory subcategory;

    @ForeignIds("mobTypeIds")
    private transient @NotNull ConcurrentList<MobType> mobTypes = Concurrent.newList();

    public int getMaxKills() {
        return BRACKETS
            .get(this.getBracket() - 1)
            .get(this.getMaxTier() - 1);
    }

    public @NotNull Optional<BestiarySubcategory> getSubcategory() {
        return Optional.ofNullable(this.subcategory);
    }

    public @NotNull ConcurrentList<Integer> getTiers() {
        return BRACKETS.get(this.getBracket() - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        BestiaryFamily that = (BestiaryFamily) o;

        return this.getBracket() == that.getBracket()
            && this.getMaxTier() == that.getMaxTier()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getDescription(), that.getDescription())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getCategoryId(), that.getCategoryId())
            && Objects.equals(this.getSubcategoryId(), that.getSubcategoryId())
            && Objects.equals(this.getMobTypeIds(), that.getMobTypeIds())
            && Objects.equals(this.getMobs(), that.getMobs());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getFormat(), this.getBracket(), this.getMaxTier(), this.getCategoryId(), this.getSubcategoryId(), this.getMobTypeIds(), this.getMobs());
    }

    public static final @NotNull ConcurrentList<ConcurrentList<Integer>> BRACKETS = Concurrent.newUnmodifiableList(
        Concurrent.newUnmodifiableList(
            20, 40, 60, 100, 200,
            400, 800, 1_400, 2_000, 3_000,
            6_000, 12_000, 20_000, 30_000, 40_000,
            50_000, 60_000, 72_000, 86_000, 100_000,
            200_000, 400_000, 600_000, 800_000, 1_000_000
        ),
        Concurrent.newUnmodifiableList(
            5, 10, 15, 25, 50,
            100, 200, 350, 500, 750,
            1_500, 3_000, 5_000, 7_500, 10_000,
            12_500, 15_000, 18_000, 21_500, 25_000,
            50_000, 100_000, 150_000, 200_000, 250_000
        ),
        Concurrent.newUnmodifiableList(
            4, 8, 12, 16, 20,
            40, 80, 140, 200, 300,
            600, 1_200, 2_000, 3_000, 4_000,
            5_000, 6_000, 7_200, 8_600, 10_000,
            20_000, 40_000, 60_000, 80_000, 100_000
        ),
        Concurrent.newUnmodifiableList(
            2, 4, 6, 10, 15,
            20, 25, 35, 50, 75,
            150, 300, 500, 750, 1_000,
            1_350, 1_650, 2_000, 2_500, 3_000,
            5_000, 10_000, 15_000, 20_000, 25_000
        ),
        Concurrent.newUnmodifiableList(
            1, 2, 3, 5, 7,
            10, 15, 20, 25, 30,
            60, 120, 200, 300, 400,
            500, 600, 720, 860, 1_000,
            2_000, 4_000, 6_000, 8_000, 10_000
        ),
        Concurrent.newUnmodifiableList(
            1, 2, 3, 5, 7,
            9, 14, 17, 21, 25,
            50, 80, 125, 175, 250,
            325, 425, 525, 625, 750,
            1_500, 3_000, 4_500, 6_000, 7_500
        ),
        Concurrent.newUnmodifiableList(
            1, 2, 3, 5, 7,
            9, 11, 14, 17, 20,
            30, 40, 55, 75, 100,
            150, 200, 275, 375, 500,
            1_000, 1_500, 2_000, 2_500, 3_000
        )
    );

}
