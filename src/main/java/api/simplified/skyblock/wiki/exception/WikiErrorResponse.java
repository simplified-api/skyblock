package api.simplified.skyblock.wiki.exception;

import api.simplified.skyblock.wiki.response.WikiError;
import api.simplified.skyblock.wiki.response.WikiResponse;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.NoArgsConstructor;
import dev.simplified.client.exception.ApiErrorResponse;
import org.jetbrains.annotations.NotNull;

/**
 * The body of a failed wiki request, read as a MediaWiki error envelope,
 * {@code {"error": {"code": "...", "info": "..."}}}.
 *
 * <p>
 * A body that is not one - the HTML page the wiki's origin or Cloudflare answers most error
 * statuses with - leaves {@link #getError()} empty and the reason at its fallback.
 *
 * @see WikiApiException
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WikiErrorResponse extends WikiResponse implements ApiErrorResponse {

    /**
     * Reads the account the wiki gave of the failure.
     *
     * @return the error's info, or a fallback when the body carried no MediaWiki error
     */
    @Override
    public @NotNull String getReason() {
        return this.getError()
            .map(WikiError::getInfo)
            .filter(info -> !info.isEmpty())
            .orElse("Unknown (body missing or not a MediaWiki error)");
    }

}
