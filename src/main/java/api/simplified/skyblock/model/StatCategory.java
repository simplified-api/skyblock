package api.simplified.skyblock.model;

import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.persistence.Hydration;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lib.minecraft.text.ChatColor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * The tab a stat is grouped under in the stats menu - Combat, Mining, Fishing and so on.
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "stat_categories")
@Hydration(every = 10, unit = TimeUnit.MINUTES)
public class StatCategory implements JpaModel {

    /**
     * The category's id, the value a {@link Stat} stores to say which tab it belongs to.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the category.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The colour the category's heading is drawn in.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private @NotNull ChatColor.Legacy format = ChatColor.Legacy.WHITE;

}