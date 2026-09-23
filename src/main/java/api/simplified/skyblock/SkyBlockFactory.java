package api.simplified.skyblock;

import api.simplified.github.GitHubCorpus;
import api.simplified.skyblock.model.Item;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.RepositoryFactory;
import dev.simplified.persistence.source.DocumentSource;
import dev.simplified.persistence.source.Source;
import dev.simplified.persistence.source.WritableDocumentSource;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Repository factory for the SkyBlock models scoped to the {@link Item} package, reading each one out
 * of the corpus published in the data repository.
 *
 * <p>One {@link Source} serves every model. A read is handed the type it wants, the corpus catalogue
 * names that type's document and the layers it merges from, and nothing here has to know either.
 *
 * <p>The no-argument constructor reads the published corpus unauthenticated, which GitHub limits to
 * 60 requests per hour per IP - enough for a single session, not for a suite that connects
 * repeatedly. A caller with a token names one on {@link #corpus()}; a caller with an origin of its
 * own passes a {@link Source} instead.
 */
@Getter
public class SkyBlockFactory implements RepositoryFactory {

    /**
     * The environment variable holding the GitHub personal access token, if one is set.
     */
    public static final @NotNull String TOKEN_VARIABLE = "SKYBLOCK_GITHUB_TOKEN";

    private static final @NotNull String OWNER = "simplified-api";
    private static final @NotNull String REPOSITORY = "skyblock";
    private static final @NotNull String MANIFEST_PATH = "data/v1/index.json";

    /**
     * The published corpus every SkyBlock model is read out of.
     */
    public static final @NotNull GitHubCorpus CORPUS = corpus().build();

    private final @NotNull ConcurrentList<Class<JpaModel>> models = RepositoryFactory.resolveModels(Item.class);
    private final @NotNull Optional<Source> source;

    /**
     * Constructs a factory reading the published corpus.
     */
    public SkyBlockFactory() {
        this(CORPUS);
    }

    /**
     * Constructs a factory reading the given corpus.
     *
     * @param corpus the repository the documents are published from
     */
    public SkyBlockFactory(@NotNull GitHubCorpus corpus) {
        this(new DocumentSource(new CorpusOrigin(corpus), corpusSettings().create()));
    }

    /**
     * Constructs a factory reading the given origin.
     *
     * @param source where every model's rows come from
     */
    public SkyBlockFactory(@NotNull Source source) {
        this.source = Optional.of(source);
    }

    /**
     * Returns a factory that also writes the given corpus back.
     *
     * <p>Which of these two a caller builds is the whole of the difference between a deployment that
     * reads the corpus and the one that maintains it. Nothing downstream can turn one into the other,
     * because the write instruction is a property of the source rather than a setting on it.
     *
     * @param corpus the repository the documents are published from, named with a token
     * @return a factory reading and writing that corpus
     */
    public static @NotNull SkyBlockFactory writing(@NotNull GitHubCorpus corpus) {
        return new SkyBlockFactory(new WritableDocumentSource(new CorpusOrigin.Writing(corpus), corpusSettings().create()));
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
     * The settings corpus documents are parsed with.
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
