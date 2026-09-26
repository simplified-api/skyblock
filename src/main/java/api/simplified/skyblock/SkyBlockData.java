package api.simplified.skyblock;

import api.simplified.github.GitHubCorpus;
import api.simplified.skyblock.model.Item;
import dev.simplified.annotations.UtilityClass;
import dev.simplified.collection.query.Indexed;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.Hydration;
import dev.simplified.persistence.JpaConfig;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.JpaSession;
import dev.simplified.persistence.Linked;
import dev.simplified.persistence.Repository;
import dev.simplified.persistence.SessionManager;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.DocumentSource;
import dev.simplified.persistence.source.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static locator for the SkyBlock corpus.
 * <p>
 * Holds the one session every SkyBlock model is read through, on a {@link SessionManager} that holds
 * nothing else, names the corpus published in the data repository, and exposes repository access
 * plus the session bootstrap. Call {@link #connect()} at startup before any
 * {@link #getRepository(Class)} lookup against a SkyBlock model.
 * <p>
 * The corpus connects once per JVM. The first connect to succeed reads it and holds the session;
 * every later connect returns that session and reads nothing, whichever origin it names, so no caller
 * can force the corpus to be re-read or open a second session over it. Nothing disconnects the
 * session: the manager's JVM shutdown hook shuts it down at exit.
 * <p>
 * The corpus session is read-only: it is connected over a {@link DocumentSource.ReadOnly}, which is
 * no {@link Source.Writable}. A caller holding a token writes the corpus back through
 * {@link JpaConfig#write} on the config {@link #writing(GitHubCorpus)} returns, which checks each
 * write against the corpus before anything is committed. One that also reads the corpus connects
 * that config on a {@link SessionManager} of its own -
 * {@code new SessionManager().connect(SkyBlockData.writing(corpus))} - and writes through that
 * session, and a caller with tables of its own connects and reads them on its own manager too.
 * <p>
 * One {@link Source} serves every model. A read is handed the type it wants, the corpus catalogue
 * names that type's document and the layers it merges from, and nothing here has to know either.
 * <p>
 * Each JVM that connects holds every row of every model in memory, once, and each {@link Linked}
 * field points at the row its target's repository holds rather than at a copy of it. That footprint
 * is what the corpus costs a consumer, and a lookup performs no I/O.
 * <p>
 * Every model declares a ten-minute {@link Hydration} cadence, so a connected session keeps up with
 * the published corpus rather than holding what it first read for its whole life. Each tick asks
 * whether the corpus branch moved, which is one request. A tick whose branch has not moved reads
 * nothing more; one whose branch moved reads the catalogue at the new tip, then only the documents
 * whose fingerprint moved, each with every document linking into it.
 */
@UtilityClass
public class SkyBlockData {

    /**
     * The catalogue naming every document the corpus publishes, relative to the repository root.
     */
    static final @NotNull String MANIFEST_PATH = "data/v1/index.json";

    private static final @NotNull String OWNER = "simplified-api";
    private static final @NotNull String REPOSITORY = "skyblock";

    /**
     * The manager holding the corpus session and nothing else.
     */
    private static final @NotNull SessionManager sessionManager = new SessionManager();

    /**
     * The corpus session, held from the first connect that succeeds.
     */
    private static @Nullable JpaSession session;

    /**
     * Retrieves the {@link Repository} holding all entities of the given model type.
     *
     * <p>
     * The rows are held in memory, so no finder performs I/O. Every SkyBlock model declares its id
     * {@link Indexed} and unique, a stat also its name and a region also its mode, so an equality
     * finder over one of those probes a hash and any other finder scans the held rows. A held row is
     * as old as the last hydration of its type, which {@link Repository#getHydratedAt()} reports.
     *
     * @param tClass the {@link JpaModel} class to find a repository for
     * @param <T> the entity type
     * @return the repository holding entities of type {@code T}
     */
    public static <T extends JpaModel> @NotNull Repository<T> getRepository(@NotNull Class<T> tClass) {
        return sessionManager.getRepository(tClass);
    }

    /**
     * Connects the SkyBlock session over the corpus published on GitHub, or returns the session an
     * earlier connect holds.
     *
     * <p>This is {@link #connect(DocumentSource.ReadOnly.Builder)} over the published corpus, and the
     * first connect in a JVM wins in the same way: a later connect returns the held session, builds no
     * corpus and makes no request.
     *
     * <p>No database is opened: the rows the corpus publishes are held in memory and every finder
     * answers from them. The corpus is read unauthenticated, which GitHub limits to 60 requests per
     * hour per IP. The connect that reads spends 37 of them - the branch tip, the catalogue at that
     * tip, 34 primary documents and one extra layer - and the ten-minute cadence one more per tick,
     * six an hour, plus the catalogue and the moved documents at a tick that finds the branch moved.
     *
     * @return the corpus session
     * @throws JpaException if this call is the one that connects and a model fails to read or link, in
     *         which case nothing is held and the next connect tries again
     */
    public static synchronized @NotNull JpaSession connect() {
        return session != null ? session : connect(CorpusOrigin.reading(corpus().build()));
    }

    /**
     * Connects the SkyBlock session over the source the given builder describes, or returns the
     * session an earlier connect holds.
     *
     * <p>The first connect in a JVM wins. It registers every model under the {@link Item} package with
     * the {@link SessionManager} and hydrates each one from the layers the source names, parsed with
     * {@link #corpusSettings()} whatever parser the builder held. Every later connect, over this source
     * or any other, returns the session that connect holds, never builds its own source and never asks
     * it anything. Concurrent first calls connect once: one reads and the others return its session. A
     * connect that fails holds nothing, so the next one tries again.
     *
     * <p>Nothing disconnects the session. The manager's JVM shutdown hook shuts it down at exit.
     *
     * @param source where each document's layers are read from, built only when this call is the one
     *        that connects
     * @return the corpus session
     * @throws JpaException if this call is the one that connects and a model fails to read or link, in
     *         which case nothing is held and the next connect tries again
     */
    public static synchronized @NotNull JpaSession connect(@NotNull DocumentSource.ReadOnly.Builder source) {
        if (session == null) {
            session = sessionManager.connect(new JpaConfig(
                JpaModel.resolveModels(Item.class),
                source.withGson(corpusSettings().create()).build()
            ));
        }

        return session;
    }

    /**
     * Returns the config registering every model under the {@link Item} package over a source that
     * reads the given corpus and also writes it back.
     *
     * <p>The write a caller holding a token makes is {@link JpaConfig#write} on this config. It is
     * checked against the corpus before anything is committed: an upsert whose rows name, through a
     * {@link Linked} field that is neither a list nor optional, a row the corpus does not carry is
     * refused, and so is a delete of a row such a field still names. A write straight through
     * {@link JpaConfig#source()} is not checked.
     *
     * <p>The check reads the documents it needs when the write is made, at the branch tip the
     * corpus's read client answers. That client can answer the tip from its response cache for the
     * {@code max-age} GitHub sends with it, so a commit another writer lands can go unseen that long.
     * A corpus {@link GitHubCorpus.Builder#build()} made drops that cache after each of its own
     * writes, so a check after one reads the tip from GitHub, and one over contracts the caller
     * supplies drops nothing. A commit that lands only after such a write gave up on its answer can
     * go unseen as long as another writer's can, and one landing between the check's read and the
     * write's commit is not seen either.
     *
     * <p>A caller that also reads the corpus connects this config on a {@link SessionManager} of its
     * own - {@code new SessionManager().connect(SkyBlockData.writing(corpus))} - and writes through
     * that session, which checks a write the same way against the rows it holds and rebuilds what it
     * changed.
     *
     * <p>Which source a caller builds is the whole of the difference between a deployment that reads
     * the corpus and the one that maintains it. Nothing downstream can turn one into the other: only
     * a read-write builder takes the write instruction, and a built source cannot be built again.
     *
     * @param corpus the repository the documents are published from, named with a token
     * @return a config registering every model over a source reading and writing that corpus
     */
    public static @NotNull JpaConfig writing(@NotNull GitHubCorpus corpus) {
        return new JpaConfig(
            JpaModel.resolveModels(Item.class),
            CorpusOrigin.writing(corpus).withGson(corpusSettings().create()).build()
        );
    }

    /**
     * Names the published corpus, leaving the token and the branch to the caller.
     *
     * <p>The repository, the catalogue path and the parser are what make it this corpus rather than
     * any other, so they are bound here; a caller adds what belongs to it and builds.
     *
     * @return a builder over the SkyBlock data repository
     */
    public static @NotNull GitHubCorpus.Builder corpus() {
        return GitHubCorpus.of(OWNER, REPOSITORY)
            .manifest(MANIFEST_PATH)
            .gson(corpusSettings());
    }

    /**
     * Builds the settings corpus documents are parsed with.
     *
     * <p>Empty strings have to round-trip rather than reading as absent, because a corpus column
     * declared non-null takes one and a null fails the write.
     *
     * @return the corpus parser settings
     */
    public static @NotNull GsonSettings corpusSettings() {
        return GsonSettings.defaults()
            .mutate()
            .withStringType(GsonSettings.StringType.DEFAULT)
            .build();
    }

}
