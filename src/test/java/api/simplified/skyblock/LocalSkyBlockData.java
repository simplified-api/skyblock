package api.simplified.skyblock;

import api.simplified.github.ManifestIndex;
import api.simplified.skyblock.model.Item;
import com.google.gson.Gson;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaConfig;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.JpaSession;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.DocumentOrigin;
import dev.simplified.persistence.source.DocumentSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A SkyBlock session whose corpus is the {@code data/v1} tree this repository ships rather than the
 * one published over the GitHub Contents API.
 * <p>
 * Only where the layers are read from differs, so this hands a {@link DocumentSource} an origin
 * pointed at disk - which is what lets a suite run with no request leaving the machine.
 * Unauthenticated GitHub reads are capped at sixty an hour and one connect spends about forty-two of
 * them, so a suite that connects at all has to connect to disk.
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

    private LocalSkyBlockData() {}

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

        if (!Files.isReadable(root.resolve(SkyBlockData.MANIFEST_PATH)))
            throw new JpaException("No corpus manifest under '%s' - name the checkout with -D%s", root, ROOT_PROPERTY);

        return root;
    }

    /**
     * Models this build declares that the checkout's catalogue carries no document for.
     * <p>
     * Every type is read during the connect, so one uncovered model fails the whole thing. It means
     * the models and the corpus are of different vintages, which regenerating the catalogue is what
     * fixes.
     *
     * @param root the checkout root
     * @return the uncovered document names, empty when the two agree
     */
    public static @NotNull ConcurrentList<String> uncoveredModels(@NotNull Path root) {
        ManifestIndex manifest = readManifest(root);

        return JpaModel.resolveModels(Item.class)
            .stream()
            .map(JpaModel::documentOf)
            .filter(name -> manifest.layersOf(name).isEmpty())
            .collect(Concurrent.toList());
    }

    /**
     * Opens a session reading every type out of the checkout.
     *
     * @param root the checkout root
     * @return the registered session, which the caller owns and must shut down
     */
    public static @NotNull JpaSession connect(@NotNull Path root) {
        return SkyBlockData.getSessionManager().connect(new JpaConfig(
            JpaModel.resolveModels(Item.class),
            new DocumentSource(new Checkout(root), SkyBlockData.corpusSettings().create())
        ));
    }

    /**
     * Closes a session and unregisters it, so a later test class sees no active session.
     *
     * @param session the session to close, null when the connect never happened
     */
    public static void disconnect(@Nullable JpaSession session) {
        if (session != null)
            SkyBlockData.getSessionManager().shutdown(session);
    }

    private static @NotNull ManifestIndex readManifest(@NotNull Path root) {
        Gson gson = GsonSettings.defaults().create();
        return gson.fromJson(read(root.resolve(SkyBlockData.MANIFEST_PATH), SkyBlockData.MANIFEST_PATH), ManifestIndex.class);
    }

    private static @NotNull String read(@NotNull Path path, @NotNull String reported) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new JpaException(exception, "Unable to read '%s' from the local corpus", reported);
        }
    }

    /**
     * A checkout answering the same two questions a published corpus does.
     */
    private record Checkout(@NotNull Path root) implements DocumentOrigin {

        @Override
        public @NotNull ConcurrentList<String> layersOf(@NotNull String name) {
            return readManifest(this.root())
                .layersOf(name)
                .stream()
                .map(ManifestIndex.Layer::path)
                .collect(Concurrent.toUnmodifiableList());
        }

        @Override
        public @NotNull String read(@NotNull String path) {
            return LocalSkyBlockData.read(this.root().resolve(path), path);
        }

    }

}
