package dev.sbs.minecraftapi.persistence;

import dev.sbs.api.collection.concurrent.ConcurrentList;
import dev.sbs.api.persistence.JpaModel;
import dev.sbs.api.persistence.RepositoryFactory;
import dev.sbs.api.persistence.strategy.RefreshStrategy;
import dev.sbs.minecraftapi.persistence.model.Item;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Repository factory for SkyBlock JSON-backed models scoped to the
 * {@link Item} package.
 */
@Getter
public class SkyBlockFactory implements RepositoryFactory {

    private final @NotNull ConcurrentList<Class<JpaModel>> models = RepositoryFactory.resolveModels(Item.class);
    private final @NotNull RefreshStrategy<?> defaultStrategy = RefreshStrategy.json("skyblock");

}
