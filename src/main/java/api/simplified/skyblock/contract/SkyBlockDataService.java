package api.simplified.skyblock.contract;

import api.simplified.github.GitHubContentsContract;
import api.simplified.github.GitHubContentsWriteContract;
import api.simplified.github.exception.GitHubApiException;
import api.simplified.github.request.PutContentRequest;
import api.simplified.github.response.GitHubCommit;
import api.simplified.github.response.GitHubContentEnvelope;
import api.simplified.github.response.GitHubPutResponse;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Service-class façade over the generic GitHub contracts that pre-binds the SkyBlock-Simplified
 * data repository as the owner and repo of every call.
 *
 * <p>Constructor-injected with a read-side and a write-side Feign proxy, each of which is
 * built independently against {@link GitHubContentsContract} and
 * {@link GitHubContentsWriteContract} respectively (each with its own {@code Accept} header).
 * Methods delegate to the appropriate proxy and pre-fill {@value #OWNER} / {@value #REPO}.
 *
 * <p>Sibling alternative: {@link SkyBlockDataContract} exposes the same surface as a Feign
 * sub-interface façade. The class variant avoids the multi-inheritance type hierarchy and
 * gives callers a stable concrete type; the interface variant lets call sites depend on a type
 * rather than a concrete class.
 *
 * @see SkyBlockDataContract
 */
@RequiredArgsConstructor
public final class SkyBlockDataService {

    /**
     * The GitHub organization that owns the data repo.
     */
    public static final @NotNull String OWNER = "simplified-api";

    /**
     * The repo name that hosts the SkyBlock JSON data corpus.
     */
    public static final @NotNull String REPO = "skyblock-data";

    /**
     * Read-side proxy configured with {@code Accept: application/vnd.github.raw+json}.
     */
    private final @NotNull GitHubContentsContract read;

    /**
     * Write-side proxy configured with {@code Accept: application/vnd.github+json}.
     */
    private final @NotNull GitHubContentsWriteContract write;

    /**
     * Fetches the current tip commit on {@code master} of the SkyBlock data repo.
     *
     * @return the current tip commit
     * @throws GitHubApiException on any non-2xx status
     */
    public @NotNull GitHubCommit getLatestMasterCommit() throws GitHubApiException {
        return this.read.getLatestMasterCommit(OWNER, REPO);
    }

    /**
     * Fetches the raw file body at the given path on {@code master} of the SkyBlock data repo.
     *
     * @param path the repo-root-relative file path
     * @return the raw file body bytes
     * @throws GitHubApiException on any non-2xx status
     */
    public byte @NotNull [] getFileContent(@NotNull String path) throws GitHubApiException {
        return this.read.getFileContent(OWNER, REPO, path);
    }

    /**
     * Fetches the Contents API JSON envelope for the given path on {@code master} of the
     * SkyBlock data repo.
     *
     * @param path the repo-root-relative file path
     * @return the Contents API envelope
     * @throws GitHubApiException on any non-2xx status
     */
    public @NotNull GitHubContentEnvelope getFileMetadata(@NotNull String path) throws GitHubApiException {
        return this.write.getFileMetadata(OWNER, REPO, path);
    }

    /**
     * Writes a new version of the file at the given path on the SkyBlock data repo via the
     * Contents API {@code PUT} endpoint.
     *
     * @param path the repo-root-relative file path
     * @param body the PUT body carrying message, base64 content, and blob SHA
     * @return the GitHub PUT response envelope
     * @throws GitHubApiException on any non-2xx status (including 409 Conflict for SHA mismatch)
     */
    public @NotNull GitHubPutResponse putFileContent(
        @NotNull String path,
        @NotNull PutContentRequest body
    ) throws GitHubApiException {
        return this.write.putFileContent(OWNER, REPO, path, body);
    }

}
