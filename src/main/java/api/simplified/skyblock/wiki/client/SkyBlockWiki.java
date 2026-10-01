package api.simplified.skyblock.wiki.client;

import api.simplified.skyblock.wiki.SkyBlockWikiContract;
import api.simplified.skyblock.wiki.exception.WikiApiException;
import dev.simplified.annotations.UtilityClass;
import dev.simplified.client.Client;
import dev.simplified.client.ClientConfig;
import dev.simplified.gson.GsonSettings;
import feign.gson.GsonDecoder;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the client every call against the Hypixel SkyBlock wiki goes through.
 *
 * <p>
 * The client carries what MediaWiki asks of an automated caller and what the contract needs to read
 * the answers:
 * <ul>
 *     <li><b>A descriptive {@code User-Agent}</b>, {@link #USER_AGENT}, naming the project and where
 *     to reach it, which the wiki's operators ask of every tool and which a library's default does
 *     not give.</li>
 *     <li><b>A text decoder</b> for the rendered pages and page sources the contract answers as
 *     {@link String}, and the JSON decoder for everything else, over {@link #settings()}.</li>
 *     <li><b>Error decoding</b> into {@link WikiApiException} for every error status the client does
 *     not raise itself.</li>
 * </ul>
 * The rate limits live on {@link SkyBlockWikiContract}'s routes.
 *
 * <p>
 * The client caps no response body: a Feign answer is read whole, so a module source of two
 * megabytes and a rendered page of half a megabyte are answered as they are. Nothing waits on the
 * wiki while building: the client starts a DNS lookup and a {@code HEAD} probe of the host on a
 * background thread and drops its failure, so the first request is what reports an unreachable wiki.
 */
@UtilityClass
public class SkyBlockWiki {

    /**
     * The {@code User-Agent} every request carries: the project and the address it is reached at.
     */
    public static final @NotNull String USER_AGENT = "simplified-api-skyblock (https://github.com/simplified-api/skyblock)";

    /**
     * Builds a client over {@link SkyBlockWikiContract}.
     *
     * <p>
     * Each call builds a client of its own, with its own connection pool, response cache and
     * rate-limit buckets; a caller keeps the one it builds.
     *
     * @return the client
     */
    public static @NotNull Client<SkyBlockWikiContract> client() {
        return Client.create(config());
    }

    /**
     * Builds the configuration {@link #client()} creates its client from: the header, the decoders
     * and the settings, with the routes and rate limits {@link SkyBlockWikiContract} declares.
     *
     * <p>
     * Building it opens no connection; only {@link Client#create(ClientConfig)} reaches for the wiki.
     *
     * @return the configuration
     */
    public static @NotNull ClientConfig<SkyBlockWikiContract> config() {
        return ClientConfig.builder(SkyBlockWikiContract.class, settings())
            .withHeader("User-Agent", USER_AGENT)
            .withDecoderFactory(gson -> new WikiDecoder(new GsonDecoder(gson)))
            .withErrorDecoder(WikiApiException::new)
            .build();
    }

    /**
     * Builds the settings the wiki's JSON answers are read with.
     *
     * <p>
     * These are {@link GsonSettings#defaults()} with empty strings read as they stand: the defaults
     * read an empty string as absent, and a rendered text, a snippet or a source can be empty.
     *
     * @return the parser settings
     */
    public static @NotNull GsonSettings settings() {
        return GsonSettings.defaults()
            .mutate()
            .withStringType(GsonSettings.StringType.DEFAULT)
            .build();
    }

}
