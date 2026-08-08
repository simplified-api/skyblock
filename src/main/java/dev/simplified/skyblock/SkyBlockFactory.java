package dev.simplified.skyblock;

import api.simplified.github.GitHubAuth;
import api.simplified.github.GitHubContentsContract;
import api.simplified.github.GitHubContentsWriteContract;
import api.simplified.github.exception.GitHubApiException;
import dev.simplified.client.Client;
import dev.simplified.client.ClientConfig;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.RepositoryFactory;
import dev.simplified.persistence.source.FileFetcher;
import dev.simplified.persistence.source.IndexProvider;
import dev.simplified.persistence.source.RemoteJsonSource;
import dev.simplified.persistence.source.Source;
import dev.simplified.skyblock.contract.SkyBlockDataContract;
import dev.simplified.skyblock.model.Item;
import dev.simplified.skyblock.source.GitHubFileFetcher;
import dev.simplified.skyblock.source.GitHubIndexProvider;
import dev.simplified.util.StringUtil;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Repository factory for the SkyBlock models scoped to the {@link Item} package, loading each one
 * from the {@code skyblock-data} repository over the GitHub Contents API.
 *
 * <p>Every model gets its own {@link RemoteJsonSource} sharing one {@link IndexProvider} and one
 * {@link FileFetcher}, so the manifest that maps a model class to its file is fetched once and the
 * per-model sources differ only in which entry they claim.
 *
 * <p>The no-argument constructor builds its own GitHub clients and reads a personal access token
 * from {@value #TOKEN_VARIABLE}. Without one the reads are unauthenticated, which GitHub limits to
 * 60 requests per hour per IP - enough for a single session, not for a suite that connects
 * repeatedly. Callers that already own configured proxies should pass a
 * {@link SkyBlockDataContract} instead.
 */
@Getter
public class SkyBlockFactory implements RepositoryFactory {

    /**
     * Identifies this source in exception messages and in the external asset state tables.
     */
    public static final @NotNull String SOURCE_ID = "skyblock-data";

    /**
     * The environment variable holding the GitHub personal access token, if one is set.
     */
    public static final @NotNull String TOKEN_VARIABLE = "SKYBLOCK_DATA_GITHUB_TOKEN";

    private static final @NotNull String API_VERSION = "2022-11-28";
    private static final @NotNull String RAW_ACCEPT = "application/vnd.github.raw+json";
    private static final @NotNull String JSON_ACCEPT = "application/vnd.github+json";

    private final @NotNull ConcurrentList<Class<JpaModel>> models = RepositoryFactory.resolveModels(Item.class);
    private final @NotNull ConcurrentMap<Class<?>, Source<?>> sources;
    private final @Nullable Source<?> defaultSource = null;

    /**
     * Constructs a factory against clients it builds and owns.
     */
    public SkyBlockFactory() {
        this(buildContract());
    }

    /**
     * Constructs a factory against an already-configured data contract.
     *
     * @param contract the read and write proxies bound to the data repository
     */
    public SkyBlockFactory(@NotNull SkyBlockDataContract contract) {
        IndexProvider indexProvider = new GitHubIndexProvider(SOURCE_ID, contract, GsonSettings.defaults().create());
        FileFetcher fileFetcher = new GitHubFileFetcher(SOURCE_ID, contract);

        ConcurrentMap<Class<?>, Source<?>> sources = Concurrent.newMap();

        for (Class<JpaModel> model : this.getModels())
            sources.put(model, new RemoteJsonSource<>(SOURCE_ID, indexProvider, fileFetcher, model));

        this.sources = sources.toUnmodifiable();
    }

    /**
     * Builds the read and write proxies the data repository needs.
     *
     * <p>The two surfaces cannot share one proxy: the Contents endpoint only returns a raw body
     * above one megabyte under the raw media type, while the write surface needs the JSON envelope
     * carrying the blob sha.
     *
     * <p>No request is issued here - the client builds its proxy and connection pool on the first
     * call, so an unreachable GitHub does not prevent a session from being configured.
     *
     * @return the aggregated data contract
     */
    private static @NotNull SkyBlockDataContract buildContract() {
        GitHubAuth auth = GitHubAuth.bearer(StringUtil.stripToEmpty(System.getenv(TOKEN_VARIABLE)));
        GsonSettings gsonSettings = GsonSettings.defaults();

        Client<GitHubContentsContract> read = Client.create(
            ClientConfig.builder(GitHubContentsContract.class, gsonSettings)
                .withHeader("Accept", RAW_ACCEPT)
                .withHeader("X-GitHub-Api-Version", API_VERSION)
                .withDynamicHeader("Authorization", auth)
                .withErrorDecoder(GitHubApiException::new)
                .build()
        );

        Client<GitHubContentsWriteContract> write = Client.create(
            ClientConfig.builder(GitHubContentsWriteContract.class, gsonSettings)
                .withHeader("Accept", JSON_ACCEPT)
                .withHeader("X-GitHub-Api-Version", API_VERSION)
                .withDynamicHeader("Authorization", auth)
                .withErrorDecoder(GitHubApiException::new)
                .build()
        );

        return SkyBlockDataContract.from(read.getContract(), write.getContract());
    }

}
