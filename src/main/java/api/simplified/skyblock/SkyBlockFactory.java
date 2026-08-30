package api.simplified.skyblock;

import api.simplified.github.GitHubCorpus;
import api.simplified.skyblock.model.Item;
import dev.simplified.annotations.Getter;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.RepositoryFactory;
import dev.simplified.persistence.store.Source;
import org.jetbrains.annotations.NotNull;

/**
 * Repository factory for the SkyBlock models scoped to the {@link Item} package, reading each one out
 * of the corpus published in the data repository.
 *
 * <p>One {@link Source} serves every model. A read is handed the type it wants, the corpus catalogue
 * names that type's document and the layers it merges from, and nothing here has to know either.
 *
 * <p>The no-argument constructor reads the published corpus unauthenticated, which GitHub limits to
 * 60 requests per hour per IP - enough for a single session, not for a suite that connects
 * repeatedly. A caller with a token, or with a corpus of its own, passes a {@link Source} instead.
 */
@Getter
public class SkyBlockFactory implements RepositoryFactory {

    /**
     * The environment variable holding the GitHub personal access token, if one is set.
     */
    public static final @NotNull String TOKEN_VARIABLE = "SKYBLOCK_GITHUB_TOKEN";

    /**
     * The published corpus every SkyBlock model is read out of.
     */
    public static final @NotNull GitHubCorpus CORPUS = GitHubCorpus.of("simplified-api", "skyblock")
        .manifest("data/v1/index.json")
        .gson(corpusSettings())
        .build();

    private final @NotNull ConcurrentList<Class<JpaModel>> models = RepositoryFactory.resolveModels(Item.class);
    private final @NotNull Source source;

    /**
     * Constructs a factory reading the published corpus.
     */
    public SkyBlockFactory() {
        this(CORPUS.reading());
    }

    /**
     * Constructs a factory reading the given origin.
     *
     * @param source where every model's rows come from
     */
    public SkyBlockFactory(@NotNull Source source) {
        this.source = source;
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
