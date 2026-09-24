package api.simplified.skyblock;

import api.simplified.github.GitHubCorpus;
import api.simplified.skyblock.model.Item;
import dev.simplified.annotations.Getter;
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
import dev.simplified.persistence.source.WriteRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Static locator for the SkyBlock persistence layer.
 * <p>
 * Owns a dedicated {@link SessionManager}, names the corpus published in the data repository that
 * every SkyBlock model is read out of, and exposes repository access plus the session bootstrap. Call
 * {@link #connect()} once at startup before any {@link #getRepository(Class)} lookup against a
 * SkyBlock model.
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
     * Dedicated {@link SessionManager} owned by the persistence layer.
     */
    @Getter private static final @NotNull SessionManager sessionManager = new SessionManager();

    /**
     * Retrieves the {@link Repository} holding all entities of the given model type.
     *
     * <p>
     * The rows are held in memory, so no finder performs I/O. An equality finder over a property
     * declaring {@link Indexed} probes a hash; no SkyBlock model declares one, so every finder scans
     * the held rows. A held row is as old as the last hydration of its type, which
     * {@link Repository#getHydratedAt()} reports.
     *
     * @param tClass the {@link JpaModel} class to find a repository for
     * @param <T> the entity type
     * @return the repository holding entities of type {@code T}
     */
    public static <T extends JpaModel> @NotNull Repository<T> getRepository(@NotNull Class<T> tClass) {
        return sessionManager.getRepository(tClass);
    }

    /**
     * Applies one write through the session on this manager that registers the type, and rebuilds
     * that type and every type linking into it.
     *
     * <p>A repository is a held generation and a write does not change one, so a write goes to the
     * source that owns the rows and the next generation reflects it. A reader holding the current
     * one never sees it change underneath them.
     *
     * <p>It succeeds only for a type a session over a {@link Source.Writable} registered on this
     * manager. The corpus session {@link #connect()} registers reads a source with no write half, so a
     * write to a SkyBlock model through it is refused.
     *
     * @param request the write to apply
     * @param <T> the entity type
     * @throws JpaException if no session on this manager registers the type, its source holds no write
     *         instruction, an upserted row's link that is neither a list nor an {@link Optional}
     *         carries no id or names no row, or the write fails
     */
    public static <T extends JpaModel> void write(@NotNull WriteRequest<T> request) {
        sessionManager.write(request);
    }

    /**
     * Connects the SkyBlock session, registering every model under the {@link Item} package with the
     * {@link SessionManager} and hydrating each one from the published corpus.
     *
     * <p>No database is opened: the rows the corpus publishes are held in memory and every finder
     * answers from them. The corpus is read unauthenticated, which GitHub limits to 60 requests per
     * hour per IP. A connect spends 37 of them - the branch tip, the catalogue at that tip, 34
     * primary documents and one extra layer - and the ten-minute cadence one more per tick, six an
     * hour, plus the catalogue and the moved documents at a tick that finds the branch moved. That is
     * enough for a single session, not for a suite that connects repeatedly.
     *
     * @return the newly registered SkyBlock {@link JpaSession}
     */
    public static @NotNull JpaSession connect() {
        return sessionManager.connect(new JpaConfig(
            JpaModel.resolveModels(Item.class),
            new DocumentSource(new CorpusOrigin(corpus().build()), corpusSettings().create())
        ));
    }

    /**
     * Returns a source that reads the given corpus and also writes it back.
     *
     * <p>Which of the two a caller builds is the whole of the difference between a deployment that
     * reads the corpus and the one that maintains it. Nothing downstream can turn one into the other,
     * because the write instruction is a property of the source rather than a setting on it.
     *
     * @param corpus the repository the documents are published from, named with a token
     * @return a source reading and writing that corpus
     */
    public static @NotNull Source.Writable writing(@NotNull GitHubCorpus corpus) {
        return new DocumentSource.Writable(new CorpusOrigin.Writing(corpus), corpusSettings().create());
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
