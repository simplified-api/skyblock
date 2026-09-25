package api.simplified.skyblock;

import api.simplified.github.ManifestIndex;
import api.simplified.skyblock.model.Item;
import com.google.gson.Gson;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaConfig;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.SessionManager;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.DocumentSource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A SkyBlock corpus whose documents are the {@code data/v1} tree this repository ships rather than the
 * one published over the GitHub Contents API.
 * <p>
 * Only where the layers are read from differs, so a suite hands
 * {@link SkyBlockData#connect(DocumentSource.ReadOnly.Builder)} a {@link #checkout checkout} pointed
 * at disk - which is what lets it run with no request leaving the machine. Unauthenticated GitHub
 * requests are capped at sixty an hour and one connect makes thirty-seven of them, so a suite that
 * connects at all has to connect to disk. A checkout fingerprints nothing, so a session held here past
 * its ten-minute cadence re-reads every document at each tick.
 * <p>
 * The corpus connects once per JVM and the first connect wins. Every suite connects the same
 * checkout, so whichever runs first reads it and every later connect returns that session. A test
 * whose assertions depend on performing a connect itself builds a {@link SessionManager} of its own
 * with a {@link JpaConfig} over a {@link #checkout checkout}.
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
     * Starts a read-only source over a checkout, answering the same two questions a published corpus
     * does.
     *
     * @param root the checkout root, whose manifest names every layer read
     * @return the builder, left for the caller to give a parser and build
     */
    public static @NotNull DocumentSource.ReadOnly.Builder checkout(@NotNull Path root) {
        return DocumentSource.ReadOnly.builder()
            .withLayers(name -> readManifest(root)
                .layersOf(name)
                .stream()
                .map(ManifestIndex.Layer::path)
                .collect(Concurrent.toUnmodifiableList())
            )
            .withText(path -> read(root.resolve(path), path));
    }

}
