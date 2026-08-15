package api.simplified.skyblock.model;

import dev.simplified.annotations.EqualsAndHashCode;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

/**
 * A combination potion - a single consumable that applies several potion effects at once, each at
 * its own level. The Dungeon Potions are the canonical case.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Dungeon_Potion">Dungeon Potion</a>
 */
@Getter
@Entity
@EqualsAndHashCode(useAccessors = true)
@Table(name = "potion_groups")
public class PotionGroup implements JpaModel {

    /**
     * The combination's id.
     */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull String id = "";

    /**
     * Display name of the combination.
     */
    @Column(name = "name", nullable = false)
    private @NotNull String name = "";

    /**
     * The effects the combination applies, keyed by {@link Potion} id with the level each is applied
     * at as the value. The keys carry no association, so resolving an effect is a repository lookup
     * of its own.
     */
    @Column(name = "potions", nullable = false)
    private @NotNull ConcurrentMap<String, Integer> potions = Concurrent.newMap();

}