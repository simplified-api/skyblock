package api.simplified.github;

import org.jetbrains.annotations.NotNull;

/**
 * The way a consumer's tests reach the corpus constructor that takes its contracts.
 *
 * <p>That constructor is package-private, which keeps it out of the published API. Neither this
 * module nor the corpus's declares a module, so both load onto the test classpath as the unnamed
 * module, where a class in this package shares the package with the corpus and can call it.
 */
public final class CorpusFixture {

    private CorpusFixture() {}

    /**
     * Builds a corpus whose every request is answered by the given contracts rather than GitHub.
     *
     * @param builder the repository, branch, catalogue path and parser settings
     * @param reads the contract files and the branch tip are read through
     * @param writes the contract blob shas are read and files are written through
     * @return the corpus
     */
    public static @NotNull GitHubCorpus over(
        @NotNull GitHubCorpus.Builder builder,
        @NotNull GitHubContentsContract reads,
        @NotNull GitHubContentsWriteContract writes
    ) {
        return new GitHubCorpus(builder, reads, writes);
    }

}
