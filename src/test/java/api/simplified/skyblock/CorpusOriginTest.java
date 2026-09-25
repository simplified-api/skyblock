package api.simplified.skyblock;

import api.simplified.github.GitHubContentsContract;
import api.simplified.github.GitHubContentsWriteContract;
import api.simplified.github.GitHubCorpus;
import api.simplified.github.exception.GitHubApiException;
import api.simplified.github.request.PutContentRequest;
import api.simplified.github.response.GitHubCommit;
import api.simplified.github.response.GitHubContentEnvelope;
import api.simplified.github.response.GitHubPutResponse;
import api.simplified.skyblock.model.StatCategory;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.simplified.client.exception.ErrorContext;
import dev.simplified.client.request.HttpMethod;
import dev.simplified.client.response.HttpStatus;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.exception.JpaException;
import dev.simplified.persistence.source.WriteRequest;
import feign.Request;
import feign.RetryableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers what the corpus origin asks the repository and what it raises when a request fails: the
 * fingerprints a connect and a tick ask for, the commit a layer is read at, the catalogue a writing
 * origin refreshes before naming a document's layers, and the body a write reads and commits under.
 *
 * <p>The repository answers from memory through the same contracts the client proxies, so no request
 * leaves the machine. The corpus is the one {@link SkyBlockData#corpus()} names, on its default
 * branch, built over the repository's two contracts.
 */
class CorpusOriginTest {

    private static final @NotNull Gson WIRE = GsonSettings.defaults().create();
    private static final @NotNull Gson CORPUS = SkyBlockData.corpusSettings().create();
    private static final @NotNull String BRANCH = "master";
    private static final @NotNull String MANIFEST = SkyBlockData.MANIFEST_PATH;
    private static final @NotNull String ITEMS = "data/v1/items/items.json";
    private static final @NotNull String ITEMS_EXTRA = "data/v1/items/items_extra.json";
    private static final @NotNull String CATEGORIES = "data/v1/modifiers/stat_categories.json";

    /**
     * The stat categories at the first tip.
     */
    private static final @NotNull String CATEGORIES_AT_TIP = """
        [{"id":"OTHER","name":"Other","format":"WHITE"},{"id":"COMBAT","name":"Combat","format":"RED"}]
        """;

    /**
     * The stat categories the branch answers once a later commit added a row.
     */
    private static final @NotNull String CATEGORIES_AT_BRANCH = """
        [{"id":"OTHER","name":"Other","format":"WHITE"},{"id":"COMBAT","name":"Combat","format":"RED"},{"id":"MINING","name":"Mining","format":"BLUE"}]
        """;

    /**
     * What {@code git hash-object} answers for the bytes of {@link #CATEGORIES_AT_BRANCH}.
     */
    private static final @NotNull String CATEGORIES_AT_BRANCH_SHA = "85da316174cbacb17c3f652454aec86936ae0bdb";

    private Repository repository;
    private GitHubCorpus corpus;

    /**
     * A repository answering both contracts the client proxies from memory: a branch tip a case
     * moves, the text of each file at each ref, and every write it is sent. It records each file read
     * as {@code path@ref} and counts the tip reads, and a case can make the tip read fail.
     */
    private static final class Repository implements GitHubContentsContract, GitHubContentsWriteContract {

        private volatile @NotNull String tip = "";
        private volatile @Nullable RuntimeException failing;
        private final @NotNull ConcurrentMap<String, String> files = Concurrent.newMap();
        private final @NotNull ConcurrentList<String> fileReads = Concurrent.newList();
        private final @NotNull ConcurrentList<Put> puts = Concurrent.newList();
        private final @NotNull AtomicInteger tipReads = new AtomicInteger();

        /**
         * Holds the text of one file at one ref.
         *
         * @param ref the commit or branch the text is answered at
         * @param path the file's path
         * @param text the file's text
         */
        void hold(@NotNull String ref, @NotNull String path, @NotNull String text) {
            this.files.put(ref + ":" + path, text);
        }

        /**
         * Commits a catalogue at a new tip.
         *
         * @param commit the new tip
         * @param catalogue the catalogue's text at that tip
         */
        void commit(@NotNull String commit, @NotNull String catalogue) {
            this.hold(commit, MANIFEST, catalogue);
            this.tip = commit;
        }

        @Override
        public @NotNull GitHubCommit getLatestCommit(@NotNull String owner, @NotNull String repo, @NotNull String branch) {
            this.tipReads.incrementAndGet();
            RuntimeException failure = this.failing;

            if (failure != null)
                throw failure;

            return WIRE.fromJson(String.format("{\"sha\":\"%s\"}", this.tip), GitHubCommit.class);
        }

        @Override
        public byte @NotNull [] getFileContent(
            @NotNull String owner,
            @NotNull String repo,
            @NotNull String path,
            @NotNull String ref
        ) {
            this.fileReads.add(path + "@" + ref);
            String text = this.files.get(ref + ":" + path);

            if (text == null)
                throw new AssertionError(String.format("No case holds '%s' at '%s'", path, ref));

            return text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public @NotNull GitHubContentEnvelope getFileMetadata(
            @NotNull String owner,
            @NotNull String repo,
            @NotNull String path,
            @NotNull String branch
        ) {
            throw new AssertionError("A write takes its sha from the body it read, never from the envelope");
        }

        @Override
        public @NotNull GitHubPutResponse putFileContent(
            @NotNull String owner,
            @NotNull String repo,
            @NotNull String path,
            @NotNull PutContentRequest body
        ) {
            this.puts.add(new Put(path, body));
            return WIRE.fromJson("{}", GitHubPutResponse.class);
        }

    }

    /**
     * One write the repository was sent.
     *
     * @param path the file the write replaces
     * @param request the body the write carried
     */
    private record Put(@NotNull String path, @NotNull PutContentRequest request) {}

    @BeforeEach
    void setUp() {
        this.repository = new Repository();
        this.repository.commit("c1", catalogue("s1", "i1", "x1"));
        this.corpus = SkyBlockData.corpus().build(this.repository, this.repository);
    }

    @Test
    @DisplayName("a first check reads the branch tip, then the catalogue at that tip, and answers each document's fingerprint from it")
    void aFirstCheckReadsTheCatalogueAtTheTip() {
        ConcurrentMap<String, String> fingerprints = new CorpusOrigin(this.corpus).fingerprints();

        assertThat(fingerprints.keySet(), containsInAnyOrder("items", "stat_categories"));
        assertThat(fingerprints.get("items"), equalTo(this.corpus.manifest().fingerprintOf("items").orElseThrow()));
        assertThat(fingerprints.get("stat_categories"), equalTo(this.corpus.manifest().fingerprintOf("stat_categories").orElseThrow()));
        assertThat(this.repository.tipReads.get(), equalTo(1));
        assertThat(this.repository.fileReads, contains(MANIFEST + "@c1"));
    }

    @Test
    @DisplayName("a check against a branch that has not moved costs one request and answers the same fingerprints")
    void anUnmovedTipReadsNothingMore() {
        CorpusOrigin origin = new CorpusOrigin(this.corpus);

        ConcurrentMap<String, String> first = origin.fingerprints();
        ConcurrentMap<String, String> second = origin.fingerprints();

        assertThat(second, equalTo(first));
        assertThat(this.repository.tipReads.get(), equalTo(2));
        assertThat(this.repository.fileReads, contains(MANIFEST + "@c1"));
    }

    @Test
    @DisplayName("a check after the branch moved reads the catalogue at the new tip, where a moved extra moves its document's fingerprint")
    void aMovedTipReadsTheCatalogueAtIt() {
        CorpusOrigin origin = new CorpusOrigin(this.corpus);
        ConcurrentMap<String, String> before = origin.fingerprints();
        this.repository.commit("c2", catalogue("s1", "i1", "x2"));

        ConcurrentMap<String, String> after = origin.fingerprints();

        assertThat(after.keySet(), containsInAnyOrder("items", "stat_categories"));
        assertThat(after.get("items"), not(equalTo(before.get("items"))));
        assertThat(after.get("stat_categories"), equalTo(before.get("stat_categories")));
        assertThat(this.repository.fileReads, contains(MANIFEST + "@c1", MANIFEST + "@c2"));
    }

    @Test
    @DisplayName("a layer is read at the commit the held catalogue came from rather than at the branch, until a check moves the catalogue")
    void aLayerIsReadAtTheCatalogueCommit() {
        this.repository.hold("c1", CATEGORIES, CATEGORIES_AT_TIP);
        CorpusOrigin origin = new CorpusOrigin(this.corpus);
        origin.fingerprints();
        this.repository.commit("c2", catalogue("s2", "i1", "x1"));
        this.repository.hold("c2", CATEGORIES, CATEGORIES_AT_BRANCH);
        this.repository.hold(BRANCH, CATEGORIES, CATEGORIES_AT_BRANCH);

        assertThat(origin.read(CATEGORIES), equalTo(CATEGORIES_AT_TIP));

        origin.fingerprints();

        assertThat(origin.read(CATEGORIES), equalTo(CATEGORIES_AT_BRANCH));
        assertThat(
            this.repository.fileReads,
            contains(MANIFEST + "@c1", CATEGORIES + "@c1", MANIFEST + "@c2", CATEGORIES + "@c2")
        );
    }

    @Test
    @DisplayName("a check whose request never reaches GitHub fails as the corpus check, carrying what the client raised")
    void anUnreachableTipFailsTheCheck() {
        RetryableException refused = unreachable();
        this.repository.failing = refused;
        CorpusOrigin origin = new CorpusOrigin(this.corpus);

        JpaException failure = assertThrows(JpaException.class, origin::fingerprints);

        assertThat(failure.getMessage(), equalTo("Failed to ask whether the corpus moved"));
        assertThat(failure.getCause(), sameInstance(refused));
    }

    @Test
    @DisplayName("a check answered with a body that is no catalogue fails as the corpus check")
    void aBodyThatIsNoCatalogueFailsTheCheck() {
        this.repository.commit("c1", "null");
        CorpusOrigin origin = new CorpusOrigin(this.corpus);

        JpaException failure = assertThrows(JpaException.class, origin::fingerprints);

        assertThat(failure.getMessage(), equalTo("Failed to ask whether the corpus moved"));
        assertThat(failure.getCause(), instanceOf(IllegalStateException.class));
    }

    @Test
    @DisplayName("a check GitHub answers with an error status fails as the corpus check, naming the status and GitHub's reason")
    void anErrorStatusIsNamed() {
        GitHubApiException notFound = answered(404, "Not Found");
        this.repository.failing = notFound;
        CorpusOrigin origin = new CorpusOrigin(this.corpus);

        JpaException failure = assertThrows(JpaException.class, origin::fingerprints);

        assertThat(failure.getMessage(), equalTo("Failed to ask whether the corpus moved (HTTP 404): Not Found"));
        assertThat(failure.getCause(), sameInstance(notFound));
    }

    @Test
    @DisplayName("a writing origin asks whether the branch moved before naming a document's layers, where a reading one answers the held catalogue")
    void aWritingOriginRefreshesTheCatalogue() {
        this.repository.commit("c1", catalogue("s1", "i1"));
        CorpusOrigin reading = new CorpusOrigin(this.corpus);
        CorpusOrigin.Writing writing = new CorpusOrigin.Writing(this.corpus);

        assertThat(reading.layersOf("items"), contains(ITEMS));

        this.repository.commit("c2", catalogue("s1", "i1", "x1"));

        assertThat(reading.layersOf("items"), contains(ITEMS));
        assertThat(writing.layersOf("items"), contains(ITEMS, ITEMS_EXTRA));
        assertThat(this.repository.tipReads.get(), equalTo(2));
        assertThat(this.repository.fileReads, contains(MANIFEST + "@c1", MANIFEST + "@c2"));
    }

    @Test
    @DisplayName("a writing origin whose refresh never reaches GitHub fails naming the document it was asked about")
    void aFailedRefreshNamesTheDocument() {
        RetryableException refused = unreachable();
        this.repository.failing = refused;
        CorpusOrigin.Writing writing = new CorpusOrigin.Writing(this.corpus);

        JpaException failure = assertThrows(JpaException.class, () -> writing.layersOf("items"));

        assertThat(failure.getMessage(), equalTo("Failed to refresh the catalogue naming 'items'"));
        assertThat(failure.getCause(), sameInstance(refused));
    }

    @Test
    @DisplayName("a write commits under git's blob sha of the body it read at the branch, and sends that body with the row applied")
    void aWriteCommitsUnderTheShaOfTheBodyItChanged() {
        this.repository.hold("c1", CATEGORIES, CATEGORIES_AT_TIP);
        this.repository.hold(BRANCH, CATEGORIES, CATEGORIES_AT_BRANCH);
        StatCategory fighting = row("{\"id\":\"COMBAT\",\"name\":\"Fighting\",\"format\":\"DARK_RED\"}");

        SkyBlockData.writing(this.corpus).write(WriteRequest.upsert(StatCategory.class, List.of(fighting)));

        assertThat(this.repository.puts, hasSize(1));
        Put put = this.repository.puts.getFirst();
        ConcurrentList<StatCategory> atBranch = rows(CATEGORIES_AT_BRANCH);

        assertThat(put.path(), equalTo(CATEGORIES));
        assertThat(put.request().getBranch(), equalTo(BRANCH));
        assertThat(put.request().getMessage(), equalTo("Update " + CATEGORIES));
        assertThat(put.request().getSha(), equalTo(CATEGORIES_AT_BRANCH_SHA));
        assertThat(rows(committed(put)), contains(atBranch.getFirst(), fighting, atBranch.getLast()));
    }

    /**
     * A catalogue carrying the two documents the cases read.
     *
     * @param categories the hash of the one {@code stat_categories} layer
     * @param items the hashes of the {@code items} layers in merge order - the primary, then the extra
     * @return the catalogue's text
     */
    private static @NotNull String catalogue(@NotNull String categories, @NotNull String... items) {
        String[] paths = { ITEMS, ITEMS_EXTRA };
        ConcurrentList<String> layers = Concurrent.newList();

        for (int index = 0; index < items.length; index++)
            layers.add(String.format("{\"path\":\"%s\",\"sha256\":\"%s\"}", paths[index], items[index]));

        return String.format(
            "{\"revision\":\"\",\"documents\":{\"items\":[%s],\"stat_categories\":[{\"path\":\"%s\",\"sha256\":\"%s\"}]}}",
            String.join(",", layers),
            CATEGORIES,
            categories
        );
    }

    /**
     * What the client raises for a request that never reaches GitHub - feign's wrapping of a
     * refused connection.
     *
     * @return the failure
     */
    private static @NotNull RetryableException unreachable() {
        Request request = Request.create(
            Request.HttpMethod.GET,
            "https://api.github.com/repos/simplified-api/skyblock/commits/" + BRANCH,
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null
        );

        return new RetryableException(
            -1,
            "Connection refused executing GET " + request.url(),
            request.httpMethod(),
            new ConnectException("Connection refused"),
            (Long) null,
            request
        );
    }

    /**
     * What the client raises for an error status GitHub answers.
     *
     * @param status the HTTP status
     * @param reason the message GitHub's error body carries
     * @return the failure
     */
    private static @NotNull GitHubApiException answered(int status, @NotNull String reason) {
        return new GitHubApiException(WIRE, new ErrorContext(
            HttpStatus.of(status),
            HttpMethod.GET,
            "https://api.github.com/repos/simplified-api/skyblock/commits/" + BRANCH,
            Map.of(),
            Map.of(),
            String.format("{\"message\":\"%s\",\"documentation_url\":\"\"}", reason).getBytes(StandardCharsets.UTF_8)
        ));
    }

    /**
     * Parses one stat category the way the corpus does.
     *
     * @param json the row's text
     * @return the row
     */
    private static @NotNull StatCategory row(@NotNull String json) {
        return CORPUS.fromJson(json, StatCategory.class);
    }

    /**
     * Parses a stat categories layer the way the corpus does.
     *
     * @param text the layer's text
     * @return the layer's rows, in its order
     */
    private static @NotNull ConcurrentList<StatCategory> rows(@NotNull String text) {
        return CORPUS.fromJson(text, TypeToken.getParameterized(ConcurrentList.class, StatCategory.class).getType());
    }

    /**
     * Decodes the text a write committed from the base64 it was sent as.
     *
     * @param put the write
     * @return the committed text
     */
    private static @NotNull String committed(@NotNull Put put) {
        return new String(Base64.getDecoder().decode(put.request().getContent()), StandardCharsets.UTF_8);
    }

}
