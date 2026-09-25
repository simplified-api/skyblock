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

import java.util.function.UnaryOperator;

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
        } catch (RuntimeException exception) {
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
        } catch (RuntimeException exception) {
            throw failed(exception, "read '%s'", path);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>The corpus is polled first: one request asks whether the branch moved, and a moved branch
     * costs one more for the catalogue at its new tip. Each document's fingerprint composes the hash
     * the catalogue records for every one of its layers.
     *
     * <p>A connect asks this before it reads any model, so its first two requests - the tip, then
     * the catalogue at it - are made here. A failure on either, whether an error status, a request
     * that never reaches GitHub or a body that is no catalogue, is raised as the failed check of the
     * corpus rather than as the failure of any model.
     */
    @Override
    public @NotNull ConcurrentMap<String, String> fingerprints() {
        try {
            ManifestIndex manifest = this.corpus.poll().orElseGet(this.corpus::manifest);
            ConcurrentMap<String, String> fingerprints = Concurrent.newMap();

            for (String name : manifest.getDocuments().keySet())
                manifest.fingerprintOf(name).ifPresent(fingerprint -> fingerprints.put(name, fingerprint));

            return fingerprints;
        } catch (RuntimeException exception) {
            throw failed(exception, "ask whether the corpus moved");
        }
    }

    /**
     * Restates a failed request as a failed read or write.
     *
     * <p>An error status GitHub answered is named with its HTTP status and GitHub's reason. Any other
     * failure - a request that never reached GitHub, or a body that is no catalogue - is carried as
     * the cause, and a {@link JpaException} already names what failed and is answered as it is.
     *
     * @param exception the failure the corpus raised
     * @param what what was being attempted
     * @param args the values the description interpolates
     * @return the failure to raise in its place
     */
    static @NotNull JpaException failed(
        @NotNull RuntimeException exception,
        @NotNull String what,
        @NotNull Object... args
    ) {
        if (exception instanceof JpaException named)
            return named;

        String attempt = "Failed to " + String.format(what, args);

        if (exception instanceof GitHubApiException answered)
            return new JpaException(
                exception,
                "%s (HTTP %d): %s",
                attempt,
                answered.getStatus().getCode(),
                answered.getResponse().getReason()
            );

        return new JpaException(exception, attempt);
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
            } catch (RuntimeException exception) {
                throw failed(exception, "refresh the catalogue naming '%s'", name);
            }

            return super.layersOf(name);
        }

        /**
         * {@inheritDoc}
         *
         * <p>The file's text and its blob sha come out of one read at the branch, and the change is
         * committed under that sha as {@code Update <path>}. The sha is computed from the bytes the
         * change was applied to, so a file that moved since - or a body the client's response cache
         * replays from before the branch moved - is refused with a conflict rather than overwritten.
         */
        @Override
        public void edit(@NotNull String path, @NotNull UnaryOperator<String> change) {
            try {
                GitHubCorpus.Blob blob = this.corpus.blob(path);
                this.corpus.write(path, change.apply(blob.text()), blob.sha(), String.format("Update %s", path));
            } catch (RuntimeException exception) {
                throw failed(exception, "write '%s'", path);
            }
        }

    }

}
