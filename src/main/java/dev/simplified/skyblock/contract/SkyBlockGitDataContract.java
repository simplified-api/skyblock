package dev.simplified.skyblock.contract;

import api.simplified.github.GitHubGitDataContract;
import api.simplified.github.exception.GitHubApiException;
import api.simplified.github.request.CreateBlobRequest;
import api.simplified.github.request.CreateCommitRequest;
import api.simplified.github.request.CreateTreeRequest;
import api.simplified.github.request.UpdateRefRequest;
import api.simplified.github.response.GitBlob;
import api.simplified.github.response.GitCommit;
import api.simplified.github.response.GitRef;
import api.simplified.github.response.GitTree;
import org.jetbrains.annotations.NotNull;

/**
 * Sub-interface façade over {@link GitHubGitDataContract} that pre-binds the SkyBlock-Simplified
 * data repository as the owner and repo of every Git Data API call.
 *
 * <p>Unlike the read+write Contents surfaces, the Git Data API uses a single {@code Accept}
 * header ({@code application/vnd.github+json}) so a single Feign client can drive every method
 * inherited here. Callers can hand this interface directly to a Feign client builder.
 *
 * @see GitHubGitDataContract
 */
public interface SkyBlockGitDataContract extends GitHubGitDataContract {

    /**
     * The GitHub organization that owns the data repo.
     */
    @NotNull String OWNER = SkyBlockDataContract.OWNER;

    /**
     * The repo name that hosts the SkyBlock JSON data corpus.
     */
    @NotNull String REPO = SkyBlockDataContract.REPO;

    /**
     * Fetches the git reference for a branch by name on the SkyBlock data repo.
     *
     * @param branch the branch name
     * @return the current ref including the target commit SHA
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitRef getRef(@NotNull String branch) throws GitHubApiException {
        return this.getRef(OWNER, REPO, branch);
    }

    /**
     * Fetches a git commit object by SHA from the SkyBlock data repo.
     *
     * @param sha the commit SHA
     * @return the commit object
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitCommit getCommit(@NotNull String sha) throws GitHubApiException {
        return this.getCommit(OWNER, REPO, sha);
    }

    /**
     * Fetches a git tree object by SHA from the SkyBlock data repo.
     *
     * @param sha the tree SHA
     * @param recursive {@code "1"} to expand subtrees inline, {@code "0"} or empty otherwise
     * @return the tree object
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitTree getTree(@NotNull String sha, @NotNull String recursive) throws GitHubApiException {
        return this.getTree(OWNER, REPO, sha, recursive);
    }

    /**
     * Creates a new git blob from the supplied content on the SkyBlock data repo.
     *
     * @param body the blob content and encoding
     * @return the created blob reference
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitBlob createBlob(@NotNull CreateBlobRequest body) throws GitHubApiException {
        return this.createBlob(OWNER, REPO, body);
    }

    /**
     * Creates a new git tree on the SkyBlock data repo.
     *
     * @param body the tree entries plus optional {@code base_tree} SHA
     * @return the created tree object
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitTree createTree(@NotNull CreateTreeRequest body) throws GitHubApiException {
        return this.createTree(OWNER, REPO, body);
    }

    /**
     * Creates a new git commit on the SkyBlock data repo.
     *
     * @param body the commit metadata plus tree and parent SHAs
     * @return the created commit object
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitCommit createCommit(@NotNull CreateCommitRequest body) throws GitHubApiException {
        return this.createCommit(OWNER, REPO, body);
    }

    /**
     * Moves a branch ref to a new commit SHA on the SkyBlock data repo.
     *
     * @param branch the branch name
     * @param body the new SHA and optional force flag
     * @return the updated ref
     * @throws GitHubApiException on any non-2xx status
     */
    default @NotNull GitRef updateRef(@NotNull String branch, @NotNull UpdateRefRequest body) throws GitHubApiException {
        return this.updateRef(OWNER, REPO, branch, body);
    }

}
