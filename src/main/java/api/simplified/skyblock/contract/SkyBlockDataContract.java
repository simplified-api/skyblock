package api.simplified.skyblock.contract;

import api.simplified.github.GitHubContentsContract;
import api.simplified.github.GitHubContentsWriteContract;
import api.simplified.github.exception.GitHubApiException;
import api.simplified.github.request.PutContentRequest;
import api.simplified.github.response.GitHubCommit;
import api.simplified.github.response.GitHubContentEnvelope;
import api.simplified.github.response.GitHubPutResponse;
import org.jetbrains.annotations.NotNull;

/**
 * Sub-interface façade over the generic GitHub contracts that pre-binds the SkyBlock-Simplified
 * data repository as the owner and repo of every call.
 *
 * <p>Extends {@link GitHubContentsContract} and {@link GitHubContentsWriteContract} so the
 * inherited annotated methods carry through for any caller that wants them, while the SkyBlock-
 * shaped {@code default} methods declared here pre-fill owner=
 * {@value #OWNER}, repo={@value #REPO} so the call site reads cleanly.
 *
 * <p>This interface is a <b>type façade</b>: it cannot be handed to a single Feign client to
 * build a proxy because {@link GitHubContentsContract} expects {@code application/vnd.github.raw+json}
 * while {@link GitHubContentsWriteContract} requires {@code application/vnd.github+json}. The
 * read and write surfaces need separate Feign proxies built on the parent contracts directly,
 * aggregated through {@link #from(GitHubContentsContract, GitHubContentsWriteContract)}.
 *
 * <p>Sibling alternative: {@link SkyBlockDataService} takes the same two parent proxies via
 * constructor injection and exposes the same surface as a final class. Pick whichever ergonomics
 * fit the call site - the interface variant lets callers depend on a type rather than a concrete
 * class; the class variant avoids a multi-inheritance type hierarchy.
 *
 * @see SkyBlockDataService
 */
public interface SkyBlockDataContract extends GitHubContentsContract, GitHubContentsWriteContract {

    /**
     * The GitHub organization that owns the data repo.
     */
    @NotNull String OWNER = "simplified-api";

    /**
     * The repo name that hosts the SkyBlock JSON data corpus.
     */
    @NotNull String REPO = "skyblock-data";

    /**
     * Fetches the current tip commit on {@code master} of the SkyBlock data repo.
     *
     * @return the current tip commit
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitHubCommit getLatestMasterCommit() throws GitHubApiException {
        return this.getLatestMasterCommit(OWNER, REPO);
    }

    /**
     * Fetches the raw file body at the given path on {@code master} of the SkyBlock data repo.
     *
     * @param path the repo-root-relative file path
     * @return the raw file body bytes
     * @throws GitHubApiException on any non-2xx status
     */
    default byte @NotNull [] getFileContent(@NotNull String path) throws GitHubApiException {
        return this.getFileContent(OWNER, REPO, path);
    }

    /**
     * Fetches the Contents API JSON envelope for the given path on {@code master} of the
     * SkyBlock data repo.
     *
     * @param path the repo-root-relative file path
     * @return the Contents API envelope
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitHubContentEnvelope getFileMetadata(@NotNull String path) throws GitHubApiException {
        return this.getFileMetadata(OWNER, REPO, path);
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
    default @NotNull GitHubPutResponse putFileContent(
        @NotNull String path,
        @NotNull PutContentRequest body
    ) throws GitHubApiException {
        return this.putFileContent(OWNER, REPO, path, body);
    }

    /**
     * Composes a runtime instance from a read-side and a write-side Feign proxy. Required because
     * the two parent contracts cannot share a single Feign client; this factory aggregates two
     * proxies behind one interface.
     *
     * @param read the Feign-built proxy on {@link GitHubContentsContract} configured with the
     *             raw {@code Accept} media type
     * @param write the Feign-built proxy on {@link GitHubContentsWriteContract} configured with
     *              the JSON {@code Accept} media type
     * @return an aggregated {@code SkyBlockDataContract} instance
     */
    static @NotNull SkyBlockDataContract from(
        @NotNull GitHubContentsContract read,
        @NotNull GitHubContentsWriteContract write
    ) {
        return new SkyBlockDataContract() {
            @Override
            public @NotNull GitHubCommit getLatestMasterCommit(@NotNull String owner, @NotNull String repo) throws GitHubApiException {
                return read.getLatestMasterCommit(owner, repo);
            }

            @Override
            public byte @NotNull [] getFileContent(@NotNull String owner, @NotNull String repo, @NotNull String path) throws GitHubApiException {
                return read.getFileContent(owner, repo, path);
            }

            @Override
            public @NotNull GitHubContentEnvelope getFileMetadata(@NotNull String owner, @NotNull String repo, @NotNull String path) throws GitHubApiException {
                return write.getFileMetadata(owner, repo, path);
            }

            @Override
            public @NotNull GitHubPutResponse putFileContent(@NotNull String owner, @NotNull String repo, @NotNull String path, @NotNull PutContentRequest body) throws GitHubApiException {
                return write.putFileContent(owner, repo, path, body);
            }
        };
    }

}
