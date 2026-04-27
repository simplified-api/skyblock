package dev.sbs.skyblockdata;

import dev.sbs.skyblockdata.model.Item;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.RepositoryFactory;
import dev.simplified.persistence.source.Source;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Repository factory for SkyBlock JSON-backed models scoped to the
 * {@link Item} package.
 */
@Getter
public class SkyBlockFactory implements RepositoryFactory {

    private final @NotNull ConcurrentList<Class<JpaModel>> models = RepositoryFactory.resolveModels(Item.class);
    private final @Nullable Source<?> defaultSource = Source.json("skyblock");

}
