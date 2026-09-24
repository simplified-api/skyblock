package api.simplified.skyblock;

import api.simplified.github.GitHubCorpus;
import api.simplified.github.ManifestIndex;
import api.simplified.github.exception.GitHubApiException;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.DocumentOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The corpus a GitHub repository publishes, read as a tree of document layers.
 *
 * <p>This is the one place that speaks both languages. A repository answers paths, bytes and shas; a
 * source asks for the layers of a logical name, the text at a path and which documents moved. Neither
 * side has to know the other exists, and a failure crossing here stops being a GitHub failure and
 * becomes a read or a write that did not happen.
 */
class CorpusOrigin implements DocumentOrigin {

    /**
     * The corpus the layers are read out of.
     */
    protected final @NotNull GitHubCorpus corpus;

    CorpusOrigin(@NotNull GitHubCorpus corpus) {
        this.corpus = corpus;
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConcurrentList<String> layersOf(@NotNull String name) {
        try {
            return this.corpus.manifest()
                .layersOf(name)
                .stream()
                .map(ManifestIndex.Layer::path)
                .collect(Concurrent.toUnmodifiableList());
        } catch (GitHubApiException exception) {
            throw failed(exception, "read the catalogue naming '%s'", name);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The text is read at the commit the held catalogue was read at, never at the branch. It
     * comes out of the same tree as the fingerprint a session recorded before reading it, and a
     * commit names content that never changes, so the client's response cache cannot hand back a
     * body from before the branch moved under a fingerprint from after it.
     */
    @Override
    public @NotNull String read(@NotNull String path) {
        try {
            return this.corpus.read(path, this.corpus.manifestCommit());
        } catch (GitHubApiException exception) {
            throw failed(exception, "read '%s'", path);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The corpus is polled first: one request asks whether the branch moved, and a moved branch
     * costs one more for the catalogue at its new tip. Each document's fingerprint composes the hash
     * the catalogue records for every one of its layers.
     */
    @Override
    public @NotNull ConcurrentMap<String, String> fingerprints() {
        try {
            ManifestIndex manifest = this.corpus.poll().orElseGet(this.corpus::manifest);
            ConcurrentMap<String, String> fingerprints = Concurrent.newMap();

            for (String name : manifest.getDocuments().keySet())
                manifest.fingerprintOf(name).ifPresent(fingerprint -> fingerprints.put(name, fingerprint));

            return fingerprints;
        } catch (GitHubApiException exception) {
            throw failed(exception, "ask whether the corpus moved");
        }
    }

    /**
     * Restates a failed request as a failed read or write.
     *
     * @param exception the failure the corpus raised
     * @param what what was being attempted
     * @param args the values the description interpolates
     * @return the failure to raise in its place
     */
    static @NotNull JpaException failed(
        @NotNull GitHubApiException exception,
        @NotNull String what,
        @NotNull Object... args
    ) {
        return new JpaException(
            exception,
            "Failed to " + String.format(what, args) + " (HTTP %d): %s",
            exception.getStatus().getCode(),
            exception.getResponse().getReason()
        );
    }

    /**
     * The corpus read as a tree a caller holds instructions to update.
     *
     * <p>Building this rather than its parent is the whole of what makes a source writable, and only
     * a caller holding a token has a reason to.
     */
    static final class Writing extends CorpusOrigin implements DocumentOrigin.Writable {

        Writing(@NotNull GitHubCorpus corpus) {
            super(corpus);
        }

        /**
         * {@inheritDoc}
         *
         * <p>The catalogue is refreshed first. A write resolves the layers it reads and rewrites
         * through here, and nothing else need have refreshed the catalogue since the writer booted -
         * a source written without a session never ticks, and a session's ticks are minutes apart -
         * so without the refresh a write could resolve them from a catalogue older than the files it
         * rewrites. A read through a writing origin is refreshed too, since nothing here tells the
         * two apart; the refresh is one request while the branch has not moved.
         */
        @Override
        public @NotNull ConcurrentList<String> layersOf(@NotNull String name) {
            try {
                this.corpus.poll();
            } catch (GitHubApiException exception) {
                throw failed(exception, "refresh the catalogue naming '%s'", name);
            }

            return super.layersOf(name);
        }

        /**
         * {@inheritDoc}
         *
         * <p>A repository's precondition is the file's blob sha. Without one named, the current sha
         * is read and used, which still refuses a write over a file that moved between the read and
         * the write.
         */
        @Override
        public void write(@NotNull String path, @NotNull String content, @NotNull Optional<String> precondition) {
            try {
                this.corpus.write(
                    path,
                    content,
                    precondition.orElseGet(() -> this.corpus.metadata(path)),
                    String.format("Update %s", path)
                );
            } catch (GitHubApiException exception) {
                throw failed(exception, "write '%s'", path);
            }
        }

    }

}
