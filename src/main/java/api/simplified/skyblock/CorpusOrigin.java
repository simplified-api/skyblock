package api.simplified.skyblock;

import api.simplified.github.GitHubCorpus;
import api.simplified.github.ManifestIndex;
import api.simplified.github.exception.GitHubApiException;
import dev.simplified.annotations.UtilityClass;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.DocumentSource;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * The corpus a GitHub repository publishes, filled into a {@link DocumentSource} builder as a tree
 * of document layers.
 *
 * <p>This is the one place that speaks both languages. A repository answers paths, bytes and shas; a
 * source asks for the layers of a logical name, the text at a path and which documents moved. Neither
 * side has to know the other exists, and a failure crossing here stops being a GitHub failure and
 * becomes a read or a write that did not happen.
 */
@UtilityClass
class CorpusOrigin {

    /**
     * Fills a read-only source builder with the corpus's layers, text and fingerprints.
     *
     * @param corpus the corpus the layers are read out of
     * @return the builder, left for the caller to give a parser and build
     */
    static @NotNull DocumentSource.ReadOnly.Builder reading(@NotNull GitHubCorpus corpus) {
        return over(DocumentSource.ReadOnly.builder(), corpus);
    }

    /**
     * Fills a read-write source builder with the corpus's layers, text, fingerprints and write
     * instruction.
     *
     * <p>Building this rather than {@link #reading} is the whole of what makes a source writable, and
     * only a caller holding a token has a reason to. Its layers are resolved through
     * {@link #refreshedLayersOf}, so a write routes its rows by the catalogue at the branch tip the
     * read client answers rather than by the one the writer booted with. The client can answer that
     * tip from its response cache, so it can trail a commit another writer lands by up to the
     * {@code max-age} GitHub sends with it. A corpus {@link GitHubCorpus.Builder#build()} made drops
     * that cache after each of its own writes, so the tip after one is read from GitHub; one over
     * contracts the caller supplies drops nothing.
     *
     * @param corpus the corpus the layers are read out of and written back to
     * @return the builder, left for the caller to give a parser and build
     */
    static @NotNull DocumentSource.ReadWrite.Builder writing(@NotNull GitHubCorpus corpus) {
        return over(DocumentSource.ReadWrite.builder(), corpus)
            .withLayers(name -> refreshedLayersOf(corpus, name))
            .withEdit((path, change) -> edit(corpus, path, change));
    }

    /**
     * Fills the slots every document source builder shares with the corpus's answers.
     *
     * @param builder the builder to fill
     * @param corpus the corpus the layers are read out of
     * @param <T> the source the builder builds
     * @param <B> the builder's own type
     * @return the same builder
     */
    private static <T extends DocumentSource, B extends DocumentSource.Builder<T, B>> @NotNull B over(
        @NotNull B builder,
        @NotNull GitHubCorpus corpus
    ) {
        return builder
            .withLayers(name -> layersOf(corpus, name))
            .withText(path -> read(corpus, path))
            .withFingerprints(() -> fingerprints(corpus));
    }

    /**
     * Names the layers of a logical document from the catalogue the corpus holds.
     *
     * @param corpus the corpus the layers are read out of
     * @param name the logical document name
     * @return the layer paths in merge order, empty when the catalogue names no such document
     * @throws JpaException if the catalogue cannot be read
     */
    static @NotNull ConcurrentList<String> layersOf(@NotNull GitHubCorpus corpus, @NotNull String name) {
        try {
            return corpus.manifest()
                .layersOf(name)
                .stream()
                .map(ManifestIndex.Layer::path)
                .collect(Concurrent.toUnmodifiableList());
        } catch (RuntimeException exception) {
            throw failed(exception, "read the catalogue naming '%s'", name);
        }
    }

    /**
     * Names the layers of a logical document after refreshing the catalogue.
     *
     * <p>A write resolves the layers it reads and rewrites through here, and nothing else need have
     * refreshed the catalogue since the writer booted - a source written without a session never
     * ticks, and a session's ticks are minutes apart - so without the refresh a write could resolve
     * them from a catalogue older than the files it rewrites. A read through a read-write source is
     * refreshed too, since the layers answer both, and so is every read the check before a write
     * makes.
     *
     * <p>The refresh reads the branch tip, which the read client can answer from its response cache
     * for the {@code max-age} GitHub sends with it, a minute, so a commit another writer lands can go
     * unseen that long. A corpus {@link GitHubCorpus.Builder#build()} made drops that cache after
     * each of its own writes, so a refresh after one reaches GitHub; one over contracts the caller
     * supplies drops nothing. While the branch has not moved the refresh costs at most that one
     * request.
     *
     * @param corpus the corpus the layers are read out of
     * @param name the logical document name
     * @return the layer paths in merge order, empty when the catalogue names no such document
     * @throws JpaException if the catalogue cannot be refreshed or read
     */
    static @NotNull ConcurrentList<String> refreshedLayersOf(@NotNull GitHubCorpus corpus, @NotNull String name) {
        try {
            corpus.poll();
        } catch (RuntimeException exception) {
            throw failed(exception, "refresh the catalogue naming '%s'", name);
        }

        return layersOf(corpus, name);
    }

    /**
     * Reads the text at one path.
     *
     * <p>The text is read at the commit the held catalogue was read at, never at the branch. It comes
     * out of the same tree as the fingerprint a session recorded before reading it, and a commit names
     * content that never changes, so however long the read client's response cache holds the answer
     * it cannot hand back a body from before the branch moved under a fingerprint from after it.
     *
     * @param corpus the corpus the layers are read out of
     * @param path a path the catalogue names, relative to the repository root
     * @return the text
     * @throws JpaException if the path cannot be read
     */
    static @NotNull String read(@NotNull GitHubCorpus corpus, @NotNull String path) {
        try {
            return corpus.read(path, corpus.manifestCommit());
        } catch (RuntimeException exception) {
            throw failed(exception, "read '%s'", path);
        }
    }

    /**
     * Answers the fingerprint of every document the corpus publishes, as it stands now.
     *
     * <p>The corpus is polled first: one tip read asks whether the branch moved, and a moved branch
     * costs one more request for the catalogue at its new tip. Each document's fingerprint composes
     * the hash the catalogue records for every one of its layers.
     *
     * <p>A connect asks this before it reads any model, so its first two requests - the tip, then the
     * catalogue at it - are made here. A failure on either, whether an error status, a request that
     * never reaches GitHub or a body that is no catalogue, is raised as the failed check of the corpus
     * rather than as the failure of any model.
     *
     * @param corpus the corpus the layers are read out of
     * @return the fingerprints keyed by logical document name
     * @throws JpaException if the corpus cannot be asked
     */
    static @NotNull ConcurrentMap<String, String> fingerprints(@NotNull GitHubCorpus corpus) {
        try {
            ManifestIndex manifest = corpus.poll().orElseGet(corpus::manifest);
            ConcurrentMap<String, String> fingerprints = Concurrent.newMap();

            for (String name : manifest.getDocuments().keySet())
                manifest.fingerprintOf(name).ifPresent(fingerprint -> fingerprints.put(name, fingerprint));

            return fingerprints;
        } catch (RuntimeException exception) {
            throw failed(exception, "ask whether the corpus moved");
        }
    }

    /**
     * Replaces the text at one path with what a change makes of it.
     *
     * <p>The file's text and its blob sha come out of one read at the branch, and the change is
     * committed under that sha as {@code Update <path>}. The read client can answer that read from
     * its response cache for the {@code max-age} GitHub sends with the file, a minute, so a commit
     * another writer lands can go unseen that long. A corpus {@link GitHubCorpus.Builder#build()}
     * made drops that cache after each of its own writes, so the read after one reaches GitHub; one
     * over contracts the caller supplies drops nothing. The sha is computed from the bytes the
     * change was applied to, so a file that moved since - or a body the cache replays from before
     * another writer's commit - is refused with a conflict rather than overwritten.
     *
     * @param corpus the corpus the file is written back to
     * @param path a path the catalogue names, relative to the repository root
     * @param change what the current text becomes
     * @throws JpaException if the write fails, including when the file moved before it landed
     */
    static void edit(@NotNull GitHubCorpus corpus, @NotNull String path, @NotNull UnaryOperator<String> change) {
        try {
            GitHubCorpus.Blob blob = corpus.blob(path);
            corpus.write(path, change.apply(blob.text()), blob.sha(), String.format("Update %s", path));
        } catch (RuntimeException exception) {
            throw failed(exception, "write '%s'", path);
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

}
