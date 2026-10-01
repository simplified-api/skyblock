package api.simplified.skyblock.wiki.response;

import api.simplified.skyblock.wiki.SkyBlockWikiContract;
import api.simplified.skyblock.wiki.exception.WikiErrorException;
import api.simplified.skyblock.wiki.request.WikiRequest;
import com.google.gson.JsonObject;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentLinkedMap;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * An answer from the wiki's {@code api.php}, which carries either its result or the refusal the wiki
 * answered in its place.
 *
 * <p>
 * The wiki answers a refused request with an HTTP status of 200, so a refusal reaches the client as a
 * success and binds here rather than raising. {@link #requireSuccess()} is where it raises: every
 * method of {@link SkyBlockWikiContract} that takes a {@link WikiRequest} calls it on each answer,
 * and the four methods that take a parameter map leave it to their caller.
 */
@Getter
public abstract class WikiResponse {

    /**
     * The refusal the wiki answered in place of a result, empty when the request was answered.
     */
    private @NotNull Optional<WikiError> error = Optional.empty();

    /**
     * Asks that the wiki answered the request rather than refusing it.
     *
     * @throws WikiErrorException if the answer carries a refusal
     */
    public void requireSuccess() throws WikiErrorException {
        if (this.error.isPresent()) {
            throw new WikiErrorException(
                "The wiki refused the request with '%s' - '%s'",
                this.error.get().getCode(),
                this.error.get().getInfo()
            );
        }
    }

    /**
     * Reads a {@code continue} object as the parameters it names, each value as the text it is sent
     * as - MediaWiki writes an offset as a number and every other value as a string.
     *
     * @param continuation the {@code continue} object
     * @return the parameters in the order the object names them
     */
    static @NotNull ConcurrentLinkedMap<String, String> continuationOf(@NotNull JsonObject continuation) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();

        continuation.entrySet().forEach(entry -> parameters.put(
            entry.getKey(),
            entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : entry.getValue().toString()
        ));

        return Concurrent.newUnmodifiableLinkedMap(parameters);
    }

}
