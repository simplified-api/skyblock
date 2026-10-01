package api.simplified.skyblock.wiki.client;

import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * Decodes a wiki answer declared as a {@link String} as the body's text, and hands every other type
 * to the JSON decoder.
 *
 * <p>
 * A rendered page is HTML and a page source is wikitext or Lua, neither of which is JSON, while the
 * default decoder reads a {@code String} as a JSON string literal and fails on either. The wiki
 * answers every body in UTF-8.
 */
final class WikiDecoder implements Decoder {

    private final @NotNull Decoder json;

    /**
     * Constructs a decoder answering text itself and everything else through the given decoder.
     *
     * @param json the decoder JSON bodies are read with
     */
    WikiDecoder(@NotNull Decoder json) {
        this.json = json;
    }

    /** {@inheritDoc} */
    @Override
    public Object decode(@NotNull Response response, @NotNull Type type) throws IOException {
        if (!String.class.equals(type))
            return this.json.decode(response, type);

        if (response.body() == null)
            return "";

        try (Reader reader = response.body().asReader(StandardCharsets.UTF_8)) {
            return Util.toString(reader);
        }
    }

}
