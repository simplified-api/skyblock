package dev.sbs.minecraftapi.persistence.model;

import com.google.gson.annotations.SerializedName;
import dev.sbs.api.collection.concurrent.Concurrent;
import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.persistence.JpaModel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Objects;

@Getter
@Entity
@Table(name = "hot_potato_stats")
public class HotPotatoStat implements JpaModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "group_key", nullable = false)
    private @NotNull String groupKey = "";

    @SerializedName("stat")
    @Column(name = "stat_id", nullable = false)
    private @NotNull String statId = "";

    @Column(name = "item_types", nullable = false)
    private @NotNull ConcurrentList<String> itemTypes = Concurrent.newList();

    @Column(name = "value", nullable = false)
    private int value = 0;

    @ManyToOne
    @JoinColumn(name = "stat_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Stat stat;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        HotPotatoStat that = (HotPotatoStat) o;

        return this.getId() == that.getId()
            && this.getValue() == that.getValue()
            && Objects.equals(this.getGroupKey(), that.getGroupKey())
            && Objects.equals(this.getStatId(), that.getStatId())
            && Objects.equals(this.getItemTypes(), that.getItemTypes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getGroupKey(), this.getStatId(), this.getItemTypes(), this.getValue());
    }

}
