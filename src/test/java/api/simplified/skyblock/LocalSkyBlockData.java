package api.simplified.skyblock;

import api.simplified.skyblock.model.Item;
import com.google.gson.Gson;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaCacheProvider;
import dev.simplified.persistence.JpaConfig;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.JpaSession;
import dev.simplified.persistence.RepositoryFactory;
import dev.simplified.persistence.driver.H2MemoryDriver;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.FileFetcher;
import dev.simplified.persistence.source.IndexProvider;
import dev.simplified.persistence.source.ManifestIndex;
import dev.simplified.persistence.source.RemoteJsonSource;
import dev.simplified.persistence.source.Source;
import dev.simplified.util.Logging;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A SkyBlock session whose reference corpus is the {@code data/v1} tree this repository ships rather
 * than the GitHub Contents API.
 * <p>
 * The production factory is bound to GitHub and cannot be repointed, so this builds its own
 * {@link JpaConfig} and registers it with the same process-wide manager
 * {@link SkyBlockData#getRepository(Class)} resolves against - which is what lets a suite run with no
 * request leaving the machine. Unauthenticated GitHub reads are capped at sixty an hour and one
 * connect spends about forty-two of them, so a suite that connects at all has to connect to disk.
 * <p>
 * The manager is static, so a session opened here is visible to every other test class in the same
 * JVM. Whoever connects must {@link #disconnect(JpaSession)} before yielding.
 */
public final class LocalSkyBlockData {

    /**
     * System property naming the checkout root, for a runner whose working directory is not the
     * project.
     */
    public static final @NotNull String ROOT_PROPERTY = "skyblock.corpus.root";

    private static final @NotNull String MANIFEST_PATH = "data/v1/index.json";
    private static final @NotNull String SOURCE_ID = "skyblock-data-local";
    private static final @NotNull String SCHEMA = "skyblock_local";

    private LocalSkyBlockData() {
    }

    /**
     * Resolves the checkout the corpus is read out of.
     * <p>
     * The corpus is tracked in this repository, so an unreadable manifest is a broken checkout
     * rather than an absent option and there is nothing for a caller to fall back to.
     *
     * @return the checkout root, whose manifest is readable
     * @throws JpaException when no manifest is readable under the resolved root
     */
    public static @NotNull Path root() {
        String declared = System.getProperty(ROOT_PROPERTY);

        Path root = (declared == null || declared.isBlank())
            ? Path.of("")
            : Path.of(declared);

        root = root.toAbsolutePath().normalize();

        if (!Files.isReadable(root.resolve(MANIFEST_PATH)))
            throw new JpaException("No corpus manifest under '%s' - name the checkout with -D%s", root, ROOT_PROPERTY);

        return root;
    }

    /**
     * Models this build declares that the checkout's manifest carries no file for.
     * <p>
     * Every source is read during the connect, so one uncovered model fails the whole thing. It
     * means the models and the manifest are of different vintages, which regenerating the manifest
     * is what fixes.
     *
     * @param root the checkout root
     * @return the uncovered model names, empty when the two agree
     */
    public static @NotNull ConcurrentList<String> uncoveredModels(@NotNull Path root) {
        ConcurrentList<String> covered = readManifest(root).getFiles()
            .stream()
            .map(ManifestIndex.Entry::getModelClass)
            .collect(Concurrent.toList());

        return RepositoryFactory.resolveModels(Item.class)
            .stream()
            .map(Class::getName)
            .filter(name -> !covered.contains(name))
            .collect(Concurrent.toList());
    }

    /**
     * Opens a session reading every reference table out of the checkout.
     *
     * @param root the checkout root
     * @return the registered session, which the caller owns and must shut down
     */
    public static @NotNull JpaSession connect(@NotNull Path root) {
        ReferenceIndex.clear();

        IndexProvider indexProvider = () -> readManifest(root);
        FileFetcher fileFetcher = path -> read(root.resolve(path), path);

        ConcurrentList<Class<JpaModel>> models = RepositoryFactory.resolveModels(Item.class);
        ConcurrentMap<Class<?>, Source<?>> sources = Concurrent.newMap();

        for (Class<JpaModel> model : models)
            sources.put(model, new RemoteJsonSource<>(SOURCE_ID, indexProvider, fileFetcher, model));

        RepositoryFactory factory = new RepositoryFactory() {
            @Override
            public @NotNull ConcurrentList<Class<JpaModel>> getModels() {
                return models;
            }

            @Override
            public @NotNull ConcurrentMap<Class<?>, Source<?>> getSources() {
                return sources.toUnmodifiable();
            }
        };

        return SkyBlockData.getSessionManager().connect(
            JpaConfig.common(new H2MemoryDriver(), SCHEMA)
                .withCacheProvider(JpaCacheProvider.EHCACHE)
                .withRepositoryFactory(factory)
                .withLogLevel(Logging.Level.WARN)
                .withGsonSettings(
                    GsonSettings.defaults()
                        .mutate()
                        .withStringType(GsonSettings.StringType.DEFAULT)
                        .build()
                )
                .build()
        );
    }

    /**
     * Closes a session and unregisters it, so a later test class sees no active session.
     *
     * @param session the session to close, null when the connect never happened
     */
    public static void disconnect(@Nullable JpaSession session) {
        if (session != null) {
            SkyBlockData.getSessionManager().shutdown(session);
            ReferenceIndex.clear();
        }
    }

    private static @NotNull ManifestIndex readManifest(@NotNull Path root) {
        Gson gson = GsonSettings.defaults().create();
        return gson.fromJson(read(root.resolve(MANIFEST_PATH), MANIFEST_PATH), ManifestIndex.class);
    }

    private static @NotNull String read(@NotNull Path path, @NotNull String reported) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new JpaException(exception, "Unable to read '%s' from the local corpus", reported);
        }
    }

}
