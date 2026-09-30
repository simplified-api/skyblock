package api.simplified.skyblock.wiki.exception;

import com.google.gson.Gson;
import dev.simplified.client.exception.ApiException;
import dev.simplified.client.exception.ErrorContext;
import dev.simplified.client.exception.JsonApiException;
import dev.simplified.client.exception.NotModifiedException;
import dev.simplified.client.exception.PreconditionFailedException;
import dev.simplified.client.exception.RateLimitException;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when an HTTP request to the Hypixel SkyBlock wiki fails with an error status.
 *
 * <p>
 * Extends {@link JsonApiException}, so the full HTTP context of the failure - status, headers, body
 * and the request - is on the instance, as it is for every client's failures. The body is read as a
 * MediaWiki error envelope into a {@link WikiErrorResponse} when it is one; the wiki's origin and
 * Cloudflare answer most error statuses with an HTML page instead, which leaves the response at its
 * fallback reason.
 *
 * <p>
 * The client raises {@link NotModifiedException} for a 3xx, {@link PreconditionFailedException} for a
 * 412 and {@link RateLimitException} for a 429 before this class is reached, so an instance the client
 * raises carries any other status of 400 or above - a 404 for a page the wiki does not hold, a 414 for
 * a request line too long for the origin.
 * A refusal the wiki answers with a status of 200 is a {@link WikiErrorException} instead.
 *
 * @see WikiErrorResponse
 * @see ApiException
 */
public final class WikiApiException extends JsonApiException {

    /**
     * Constructs a new {@code WikiApiException} from the {@link Gson} the response body is read
     * with and the HTTP context of the failure.
     *
     * @param gson the Gson instance used to read the error envelope
     * @param context the HTTP context carrying status, headers, body bytes and request metadata
     */
    public WikiApiException(@NotNull Gson gson, @NotNull ErrorContext context) {
        super(context, "Wiki");
        this.resolve(gson, WikiErrorResponse.class);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull WikiErrorResponse getResponse() {
        return (WikiErrorResponse) super.getResponse();
    }

}
