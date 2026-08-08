package dev.simplified.skyblock.source;

import api.simplified.github.exception.GitHubApiException;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.FileFetcher;
import dev.simplified.persistence.source.RemoteJsonSource;
import dev.simplified.skyblock.contract.SkyBlockDataContract;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

/**
 * Fetches the raw UTF-8 body of a single file from the {@code skyblock-data} GitHub repository
 * via the Contents REST endpoint with the {@code raw+json} media type.
 *
 * <p>Implements the {@link FileFetcher} SAM so it pairs with {@link GitHubIndexProvider} inside
 * a {@link RemoteJsonSource}. Every {@link #fetchFile(String)} call forwards the
 * repo-root-relative path to the SkyBlock data contract without mutation.
 *
 * <p>Any {@link GitHubApiException} raised by the contract is re-thrown wrapped in
 * {@link JpaException} with HTTP status, source id, and path included in the message.
 *
 * @see FileFetcher
 * @see RemoteJsonSource
 */
@Log4j2
@RequiredArgsConstructor
public final class GitHubFileFetcher implements FileFetcher {

    /**
     * The human-readable source id matching {@code ExternalAssetState.sourceId}.
     */
    private final @NotNull String sourceId;

    /**
     * The SkyBlock data contract proxy for the GitHub REST API.
     */
    private final @NotNull SkyBlockDataContract contract;

    @Override
    public @NotNull String fetchFile(@NotNull String path) throws JpaException {
        try {
            byte[] bytes = this.contract.getFileContent(path);
            String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            log.debug("Fetched file '{}' from source '{}' ({} bytes)", path, this.sourceId, bytes.length);
            return body;
        } catch (GitHubApiException ex) {
            throw new JpaException(
                ex,
                "Failed to fetch file '%s' from source '%s' (HTTP %d): %s",
                path,
                this.sourceId,
                ex.getStatus().getCode(),
                ex.getResponse().getReason()
            );
        }
    }

}
