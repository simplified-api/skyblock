package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.minecraftapi.render.text.ChatFormat;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Objects;

@Getter
@Entity
@Table(name = "bestiary_subcategories")
public class BestiarySubcategory implements JpaModel {

    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatFormat format = ChatFormat.GREEN;

    @SerializedName("category")
    @Column(name = "category_id", nullable = false)
    private @NotNull String categoryId = "";

    @Column(name = "ordinal", nullable = false)
    private int ordinal = -1;

    @ManyToOne
    @JoinColumn(name = "category_id", referencedColumnName = "id", insertable = false, updatable = false)
    private @NotNull BestiaryCategory category;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        BestiarySubcategory that = (BestiarySubcategory) o;

        return this.getOrdinal() == that.getOrdinal()
            && Objects.equals(this.getId(), that.getId())
            && Objects.equals(this.getName(), that.getName())
            && Objects.equals(this.getFormat(), that.getFormat())
            && Objects.equals(this.getCategoryId(), that.getCategoryId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getFormat(), this.getCategoryId(), this.getOrdinal());
    }

}
