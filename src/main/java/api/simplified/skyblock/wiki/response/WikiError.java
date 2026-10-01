package api.simplified.skyblock.wiki.response;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import dev.simplified.annotations.Getter;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * A refusal the wiki answers in place of a result, with an HTTP status of 200.
 *
 * <p>
 * MediaWiki writes one as an object, {@code "error": {"code": "missingtitle", "info": "..."}}, and
 * names the code again in a {@code MediaWiki-API-Error} header. The Bucket module writes its own as a
 * bare string, {@code "error": "Bucket x does not exist."}, which binds as the info with an empty
 * code.
 */
@Getter
@JsonAdapter(WikiError.Deserializer.class)
public final class WikiError {

    /**
     * The machine-readable code MediaWiki names the refusal with, such as {@code missingtitle} or
     * {@code badvalue}; empty for a Bucket refusal.
     */
    private final @NotNull String code;

    /**
     * The human-readable account of the refusal.
     */
    private final @NotNull String info;

    WikiError(@NotNull String code, @NotNull String info) {
        this.code = code;
        this.info = info;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull String toString() {
        return this.code.isEmpty() ? this.info : this.code + ": " + this.info;
    }

    /**
     * Binds either form of a refusal.
     */
    static final class Deserializer implements JsonDeserializer<WikiError> {

        /** {@inheritDoc} */
        @Override
        public @NotNull WikiError deserialize(@NotNull JsonElement json, @NotNull Type type, @NotNull JsonDeserializationContext context) {
            if (json.isJsonPrimitive())
                return new WikiError("", json.getAsString());

            if (!json.isJsonObject())
                return new WikiError("", json.toString());

            JsonObject object = json.getAsJsonObject();
            return new WikiError(text(object, "code"), text(object, "info"));
        }

        private static @NotNull String text(@NotNull JsonObject object, @NotNull String key) {
            JsonElement value = object.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
        }

    }

}
