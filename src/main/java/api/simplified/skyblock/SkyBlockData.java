package api.simplified.skyblock;

import dev.simplified.annotations.Getter;
import dev.simplified.annotations.UtilityClass;
import dev.simplified.collection.query.Indexed;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaConfig;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.JpaSession;
import dev.simplified.persistence.Repository;
import dev.simplified.persistence.SessionManager;
import dev.simplified.persistence.store.Source;
import dev.simplified.persistence.store.WriteRequest;
import dev.simplified.util.Logging;
import org.jetbrains.annotations.NotNull;

/**
 * Static locator for the SkyBlock persistence layer.
 * <p>
 * Owns a dedicated {@link SessionManager} and a {@link SkyBlockFactory}, and exposes repository
 * access plus the session bootstrap. Call {@link #connect(GsonSettings)} once at startup before any
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
     * The rows are held in memory, so a finder naming an {@link Indexed}
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
     * Applies one write through the session that holds the type, and rebuilds that type.
     *
     * <p>A repository is a held generation and a write does not change one, so a write goes to the
     * origin that owns the rows and the next generation reflects it. A reader holding the current
     * one never sees it change underneath them.
     *
     * @param request the write to apply
     * @param <T> the entity type
     */
    public static <T extends JpaModel> void write(@NotNull WriteRequest<T> request) {
        sessionManager.write(request);
    }

    /**
     * Connects the SkyBlock session, registering every model with the {@link SessionManager} and
     * hydrating each one from the corpus the registered {@link SkyBlockFactory} reads.
     *
     * <p>No driver is configured, so no database is opened: the rows the corpus publishes are held
     * in memory and every finder answers from them.
     *
     * @param gsonSettings pre-configured settings carrying the SkyBlock-specific type adapters;
     *     internally mutated to {@link GsonSettings.StringType#DEFAULT} so empty strings round-trip
     * @return the newly registered SkyBlock {@link JpaSession}
     */
    public static @NotNull JpaSession connect(@NotNull GsonSettings gsonSettings) {
        return sessionManager.connect(
            JpaConfig.builder()
                .withRepositoryFactory(factory)
                .withGsonSettings(
                    gsonSettings.mutate()
                        .withStringType(GsonSettings.StringType.DEFAULT)
                        .build()
                )
                .withLogLevel(Logging.Level.WARN)
                .build()
        );
    }

}
