package api.simplified.skyblock.wiki;

import api.simplified.skyblock.wiki.client.SkyBlockWiki;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * The wiki answers the suites bind, cut down to a few entries under {@code wiki/} in the test
 * resources, and the {@link Gson} the client binds them with.
 */
public final class WikiFixtures {

    /** The Gson the client builds from {@link SkyBlockWiki#settings()}. */
    public static final @NotNull Gson GSON = SkyBlockWiki.settings().create();

    private WikiFixtures() {}

    /**
     * Reads a fixture under {@code wiki/} in the test resources.
     *
     * @param name the fixture's file name
     * @return the fixture's text
     */
    public static @NotNull String fixture(@NotNull String name) {
        try (InputStream stream = WikiFixtures.class.getResourceAsStream("/wiki/" + name)) {
            return new String(Objects.requireNonNull(stream, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

}
