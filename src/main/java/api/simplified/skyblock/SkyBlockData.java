package api.simplified.skyblock;

import dev.simplified.annotations.Getter;
import dev.simplified.annotations.UtilityClass;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.CacheMissingStrategy;
import dev.simplified.persistence.JpaCacheProvider;
import dev.simplified.persistence.JpaConfig;
import dev.simplified.persistence.JpaExclusionStrategy;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.JpaSession;
import dev.simplified.persistence.Repository;
import dev.simplified.persistence.SessionManager;
import dev.simplified.persistence.driver.H2MemoryDriver;
import dev.simplified.persistence.store.Source;
import dev.simplified.util.Logging;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Static locator for the SkyBlock persistence layer.
 * <p>
 * Owns a dedicated {@link SessionManager} and a {@link SkyBlockFactory}, and exposes
 * repository access plus the canonical H2-backed session bootstrap. Call
 * {@link #connect(GsonSettings)} (or an overload) once at startup before any
 * {@link #getRepository(Class)} lookup against a SkyBlock model.
 */
@UtilityClass
public class SkyBlockData {

    /**
     * Dedicated {@link SessionManager} owned by the persistence layer.
     */
    @Getter private static final @NotNull SessionManager sessionManager = new SessionManager();

    /**
     * {@link SkyBlockFactory} instance that resolves the SkyBlock JPA model package and the
     * {@code skyblock/} JSON {@link Source}.
     */
    @Getter private static final @NotNull SkyBlockFactory factory = new SkyBlockFactory();

    /**
     * Retrieves the {@link Repository} holding all entities of the given model type.
     *
     * <p>
     * The rows are held in memory, so a finder naming an {@link dev.simplified.collection.query.Indexed}
     * property is a hash probe and a caller resolving many ids against one table pays nothing per id.
     * A held row is as old as the last hydration of its type, which
     * {@link Repository#getHydratedAt()} reports.
     *
     * @param tClass the {@link JpaModel} class to find a repository for
     * @param <T> the entity type
     * @return the repository holding entities of type {@code T}
     */
    public static <T extends JpaModel> @NotNull Repository<T> getRepository(@NotNull Class<T> tClass) {
        return sessionManager.getRepository(tClass);
    }

    /**
     * Connects the SkyBlock H2 in-memory JPA session backed by the default
     * {@link JpaCacheProvider#EHCACHE} second-level cache provider.
     *
     * @param gsonSettings pre-configured settings carrying the SkyBlock-specific type adapters
     *     and the {@link JpaExclusionStrategy}; internally mutated to
     *     {@link GsonSettings.StringType#DEFAULT} so empty strings round-trip on
     *     {@code nullable=false} columns
     * @return the newly registered SkyBlock {@link JpaSession}
     */
    public static @NotNull JpaSession connect(@NotNull GsonSettings gsonSettings) {
        return connect(JpaCacheProvider.EHCACHE, gsonSettings);
    }

    /**
     * Connects the SkyBlock H2 in-memory JPA session, registering all SkyBlock JSON-backed
     * model repositories with the {@link SessionManager} and loading the {@code skyblock/}
     * classpath JSON resources via the registered {@link SkyBlockFactory}.
     *
     * @param provider the JCache provider that backs the Hibernate second-level cache
     * @param gsonSettings pre-configured settings carrying the SkyBlock-specific type adapters
     *     and the {@link JpaExclusionStrategy}; internally mutated to
     *     {@link GsonSettings.StringType#DEFAULT} so empty strings round-trip on
     *     {@code nullable=false} columns
     * @return the newly registered SkyBlock {@link JpaSession}
     */
    public static @NotNull JpaSession connect(@NotNull JpaCacheProvider provider, @NotNull GsonSettings gsonSettings) {

        return sessionManager.connect(
            JpaConfig.builder()
                .withDriver(new H2MemoryDriver())
                .withSchema("skyblock")
                .withCacheProvider(provider)
                .withRepositoryFactory(factory)
                .withGsonSettings(
                    gsonSettings.mutate()
                        .withStringType(GsonSettings.StringType.DEFAULT)
                        .build()
                )
                .withLogLevel(Logging.Level.WARN)
                .isUsingQueryCache()
                .isUsing2ndLevelCache()
                .withCacheConcurrencyStrategy(CacheConcurrencyStrategy.READ_WRITE)
                .withCacheMissingStrategy(CacheMissingStrategy.CREATE_WARN)
                .withQueryResultsTTL(30)
                .build()
        );
    }

}
