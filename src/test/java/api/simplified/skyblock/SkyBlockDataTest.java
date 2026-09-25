package api.simplified.skyblock;

import api.simplified.skyblock.model.Item;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaSession;
import dev.simplified.persistence.source.DocumentSource;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static api.simplified.skyblock.LocalSkyBlockData.checkout;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Pins that the corpus connects once per JVM: the first connect holds the session, and a later one
 * returns it without asking its own source anything.
 *
 * <p>Every suite in this JVM connects the same checkout, and which of them connects first depends on
 * the order they run in. Each case therefore connects the checkout itself before asserting, so the
 * session it compares against is the held one whether that call read the checkout or returned the
 * session an earlier suite connected.
 */
class SkyBlockDataTest {

    /**
     * Starts a source that publishes nothing and records every question it is asked.
     *
     * @param asked where each question is recorded
     * @return the builder
     */
    private static @NotNull DocumentSource.ReadOnly.Builder recording(@NotNull ConcurrentList<String> asked) {
        return DocumentSource.ReadOnly.builder()
            .withLayers(name -> {
                asked.add("layersOf " + name);
                return Concurrent.newList();
            })
            .withText(path -> {
                asked.add("read " + path);
                return "[]";
            })
            .withFingerprints(() -> {
                asked.add("fingerprints");
                return Concurrent.newMap();
            });
    }

    @Test
    @DisplayName("a later connect naming a different source returns the held session and never asks that source anything")
    void aLaterConnectReturnsTheHeldSession() {
        JpaSession held = SkyBlockData.connect(checkout(LocalSkyBlockData.root()));
        ConcurrentList<String> asked = Concurrent.newList();

        JpaSession later = SkyBlockData.connect(recording(asked));

        assertThat(later, is(sameInstance(held)));
        assertThat(asked, is(empty()));
        assertThat(SkyBlockData.getRepository(Item.class), is(sameInstance(held.getRepository(Item.class).orElseThrow())));
    }

    @Test
    @DisplayName("a later connect over the published corpus returns the held session")
    void aLaterPublishedConnectReturnsTheHeldSession() {
        JpaSession held = SkyBlockData.connect(checkout(LocalSkyBlockData.root()));

        assertThat(SkyBlockData.connect(), is(sameInstance(held)));
    }

}
