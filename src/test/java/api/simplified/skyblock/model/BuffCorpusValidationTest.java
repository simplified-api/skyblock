package api.simplified.skyblock.model;

import api.simplified.skyblock.LocalSkyBlockData;
import api.simplified.skyblock.SkyBlockData;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs every refusal a buff row can be checked for in this module's own vocabulary against the
 * corpus as shipped.
 * <p>
 * Both validators are pure functions over loaded rows and no production path calls them, so without
 * this a misspelled key binds in silence: a target expanding to no stat, a member id naming a group
 * nobody declares and a group two rows both declare all load and contribute nothing forever, and no
 * other test here would notice. Authoring a row is only safe once a wrong one fails something.
 * <p>
 * This is the gate rather than a load hook, and the difference is deliberate. The read that can be
 * guarded is the one the build performs; a consumer that loads the corpus for itself guards its own
 * read, and the names it spells that this module cannot see are checked where those names live.
 *
 * @see Buff.Validator
 */
class BuffCorpusValidationTest {

    private static JpaSession session;
    private static ConcurrentList<Buff> rows;

    @BeforeAll
    static void loadCorpus() {
        Path corpus = LocalSkyBlockData.root();

        ConcurrentList<String> uncovered = LocalSkyBlockData.uncoveredModels(corpus);
        assumeTrue(uncovered.isEmpty(), "the models and the manifest are of different vintages - the manifest carries no file for " + uncovered);

        session = LocalSkyBlockData.connect(corpus);
        rows = SkyBlockData.getRepository(Buff.class).findAll();
    }

    @AfterAll
    static void releaseSession() {
        LocalSkyBlockData.disconnect(session);
        session = null;
        rows = null;
    }

    @Test
    @DisplayName("the table carries rows, so an empty read cannot pass the checks below")
    void theTableCarriesRows() {
        // every assertion here is over a list, and a list of nothing satisfies all of them - so the
        // count is what separates a clean corpus from one that never arrived
        assertThat(rows.size(), is(greaterThan(0)));
    }

    @Test
    @DisplayName("every shipped row is well formed on its own")
    void everyShippedRowIsWellFormed() {
        ConcurrentList<String> refusals = Concurrent.newList();
        rows.forEach(row -> refusals.addAll(Buff.Validator.shape(row)));

        assertThat(refusals, is(empty()));
    }

    @Test
    @DisplayName("every reference a shipped row makes resolves against the table it names")
    void everyReferenceResolves() {
        assertThat(Buff.Validator.corpus(rows), is(empty()));
    }

}
