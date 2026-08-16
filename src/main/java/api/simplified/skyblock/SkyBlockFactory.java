package api.simplified.skyblock;

import api.simplified.github.GitHubAuth;
import api.simplified.github.GitHubContentsContract;
import api.simplified.github.GitHubContentsWriteContract;
import api.simplified.github.exception.GitHubApiException;
import api.simplified.skyblock.contract.SkyBlockDataContract;
import api.simplified.skyblock.model.Item;
import com.google.gson.Gson;
import dev.simplified.annotations.AccessLevel;
import dev.simplified.annotations.Getter;
import dev.simplified.annotations.Log;
import dev.simplified.annotations.RequiredArgsConstructor;
import dev.simplified.client.Client;
import dev.simplified.client.ClientConfig;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaModel;
import dev.simplified.persistence.RepositoryFactory;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.FileFetcher;
import dev.simplified.persistence.source.IndexProvider;
import dev.simplified.persistence.source.ManifestIndex;
import dev.simplified.persistence.source.RemoteJsonSource;
import dev.simplified.persistence.source.Source;
import dev.simplified.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Repository factory for the SkyBlock models scoped to the {@link Item} package, loading each one
 * over the GitHub Contents API from the corpus its {@link SkyBlockDataContract} is bound to.
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
@Log
@Getter
public class SkyBlockFactory implements RepositoryFactory {

    /**
     * Identifies this dataset in exception messages and in the external asset state tables.
     */
    public static final @NotNull String SOURCE_ID = "skyblock";

    /**
     * The environment variable holding the GitHub personal access token, if one is set.
     */
    public static final @NotNull String TOKEN_VARIABLE = "SKYBLOCK_GITHUB_TOKEN";

    private static final @NotNull String API_VERSION = "2022-11-28";
    private static final @NotNull String RAW_ACCEPT = "application/vnd.github.raw+json";
    private static final @NotNull String JSON_ACCEPT = "application/vnd.github+json";

    private final @NotNull ConcurrentList<Class<JpaModel>> models = RepositoryFactory.resolveModels(Item.class);
    private final @NotNull ConcurrentMap<Class<?>, Source<?>> sources;
    private final @Nullable Source<?> defaultSource = null;

    @Getter(AccessLevel.NONE)
    private final @NotNull ManifestSource manifestSource;

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
        this.manifestSource = new ManifestSource(SOURCE_ID, contract, GsonSettings.defaults().create());
        FileFetcher fileFetcher = fileFetcher(SOURCE_ID, contract);

        ConcurrentMap<Class<?>, Source<?>> sources = Concurrent.newMap();

        for (Class<JpaModel> model : this.getModels())
            sources.put(model, new RemoteJsonSource<>(SOURCE_ID, this.manifestSource, fileFetcher, model));

        this.sources = sources.toUnmodifiable();
    }

    /**
     * Discards the held manifest so the next load fetches a fresh one.
     */
    public void refreshManifest() {
        this.manifestSource.refresh();
    }

    /**
     * Creates an index provider that reads the corpus manifest through the given contract.
     *
     * <p>{@link RemoteJsonSource} asks for the manifest on every load and there is one source per
     * model, so the returned provider holds the parsed manifest from its first successful fetch
     * rather than re-reading it once per model.
     *
     * @param sourceId the source id carried in exception messages and asset state rows
     * @param contract the read proxy bound to the data repository
     * @param gson the instance the manifest body is deserialized with
     * @return an index provider holding the manifest after its first successful load
     */
    public static @NotNull IndexProvider indexProvider(
        @NotNull String sourceId,
        @NotNull SkyBlockDataContract contract,
        @NotNull Gson gson
    ) {
        return new ManifestSource(sourceId, contract, gson);
    }

    /**
     * Creates a file fetcher that reads one repo-root-relative path through the given contract.
     *
     * @param sourceId the source id carried in exception messages and asset state rows
     * @param contract the read proxy bound to the data repository
     * @return a file fetcher forwarding each path to the contract without mutation
     */
    public static @NotNull FileFetcher fileFetcher(@NotNull String sourceId, @NotNull SkyBlockDataContract contract) {
        return path -> fetchFile(sourceId, contract, path);
    }

    /**
     * Fetches the raw UTF-8 body of a single corpus file.
     *
     * @param sourceId the source id carried in exception messages and asset state rows
     * @param contract the read proxy bound to the data repository
     * @param path the repo-root-relative file path
     * @return the file body decoded as UTF-8
     * @throws JpaException if GitHub answers with a non-2xx status
     */
    private static @NotNull String fetchFile(
        @NotNull String sourceId,
        @NotNull SkyBlockDataContract contract,
        @NotNull String path
    ) throws JpaException {
        try {
            byte[] bytes = contract.getFileContent(path);
            String body = new String(bytes, StandardCharsets.UTF_8);
            log.debug("Fetched file '{}' from source '{}' ({} bytes)", path, sourceId, bytes.length);
            return body;
        } catch (GitHubApiException ex) {
            throw new JpaException(
                ex,
                "Failed to fetch file '%s' from source '%s' (HTTP %d): %s",
                path,
                sourceId,
                ex.getStatus().getCode(),
                ex.getResponse().getReason()
            );
        }
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
        return clients().contract();
    }

    /**
     * Builds the GitHub clients the corpus is served through, reading a personal access token
     * from {@value #TOKEN_VARIABLE}.
     *
     * <p>Public because a consumer wiring these into its own container needs the same auth,
     * media types and error decoding this class uses. Building a second set by hand is what
     * drifts: the two Accept headers are not interchangeable, and a copy that misses the raw
     * media type silently truncates a corpus file above one megabyte.
     *
     * <p>Each call builds a fresh set. Hold the result rather than calling this per use.
     *
     * @return the clients and the contract aggregating them
     */
    public static @NotNull Clients clients() {
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

        return new Clients(
            SkyBlockDataContract.from(read.getContract(), write.getContract()),
            read,
            write
        );
    }

    /**
     * The GitHub clients the corpus is served through, and the contract aggregating them.
     *
     * @param contract the read and write proxies bound to the data repository
     * @param read the read-side client, whose last response carries the conditional-request headers
     * @param write the write-side client
     */
    public record Clients(
        @NotNull SkyBlockDataContract contract,
        @NotNull Client<GitHubContentsContract> read,
        @NotNull Client<GitHubContentsWriteContract> write
    ) {}

    /**
     * An index provider reading the corpus manifest through a data contract and holding the parsed
     * result until it is discarded.
     */
    @RequiredArgsConstructor
    private static final class ManifestSource implements IndexProvider {

        /**
         * The human-readable source id matching {@code ExternalAssetState.sourceId}.
         */
        private final @NotNull String sourceId;

        /**
         * The SkyBlock data contract proxy for the GitHub REST API.
         */
        private final @NotNull SkyBlockDataContract contract;

        /**
         * The Gson instance used to deserialize the manifest body into a {@link ManifestIndex}.
         */
        private final @NotNull Gson gson;

        /**
         * The manifest held from the first successful load, or {@code null} before one.
         */
        private volatile @Nullable ManifestIndex manifest;

        /** {@inheritDoc} */
        @Override
        public @NotNull ManifestIndex loadIndex() throws JpaException {
            ManifestIndex held = this.manifest;

            if (held == null) {
                synchronized (this) {
                    if (this.manifest == null)
                        this.manifest = this.fetchIndex();

                    held = this.manifest;
                }
            }

            return held;
        }

        /**
         * Discards the held manifest so the next {@link #loadIndex()} fetches a fresh one.
         */
        void refresh() {
            this.manifest = null;
        }

        private @NotNull ManifestIndex fetchIndex() throws JpaException {
            try {
                byte[] bytes = this.contract.getFileContent(SkyBlockDataContract.MANIFEST_PATH);
                String rawJson = new String(bytes, StandardCharsets.UTF_8);
                ManifestIndex manifest = this.gson.fromJson(rawJson, ManifestIndex.class);

                if (manifest == null)
                    throw new JpaException("GitHub returned empty or unparseable manifest for source '%s'", this.sourceId);

                log.debug("Loaded manifest for source '{}' - {} entries", this.sourceId, manifest.getCount());
                return manifest;
            } catch (GitHubApiException ex) {
                throw new JpaException(
                    ex,
                    "Failed to load manifest for source '%s' (HTTP %d): %s",
                    this.sourceId,
                    ex.getStatus().getCode(),
                    ex.getResponse().getReason()
                );
            }
        }

    }

}
