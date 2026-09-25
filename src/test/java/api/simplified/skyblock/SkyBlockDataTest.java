package api.simplified.skyblock;

import api.simplified.skyblock.LocalSkyBlockData.Checkout;
import api.simplified.skyblock.model.Item;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaSession;
import dev.simplified.persistence.source.DocumentOrigin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Pins that the corpus connects once per JVM: the first connect holds the session, and a later one
 * returns it without asking its own origin anything.
 *
 * <p>Every suite in this JVM connects the same checkout, and which of them connects first depends on
 * the order they run in. Each case therefore connects the checkout itself before asserting, so the
 * session it compares against is the held one whether that call read the checkout or returned the
 * session an earlier suite connected.
 */
class SkyBlockDataTest {

    /**
     * An origin that publishes nothing and records every question it is asked.
     */
    private static final class Recording implements DocumentOrigin {

        private final @NotNull ConcurrentList<String> asked = Concurrent.newList();

        @Override
        public @NotNull ConcurrentList<String> layersOf(@NotNull String name) {
            this.asked.add("layersOf " + name);
            return Concurrent.newList();
        }

        @Override
        public @NotNull String read(@NotNull String path) {
            this.asked.add("read " + path);
            return "[]";
        }

        @Override
        public @NotNull ConcurrentMap<String, String> fingerprints() {
            this.asked.add("fingerprints");
            return Concurrent.newMap();
        }

    }

    @Test
    @DisplayName("a later connect naming a different origin returns the held session and never asks that origin anything")
    void aLaterConnectReturnsTheHeldSession() {
        JpaSession held = SkyBlockData.connect(new Checkout(LocalSkyBlockData.root()));
        Recording other = new Recording();

        JpaSession later = SkyBlockData.connect(other);

        assertThat(later, is(sameInstance(held)));
        assertThat(other.asked, is(empty()));
        assertThat(SkyBlockData.getRepository(Item.class), is(sameInstance(held.getRepository(Item.class).orElseThrow())));
    }

    @Test
    @DisplayName("a later connect over the published corpus returns the held session")
    void aLaterPublishedConnectReturnsTheHeldSession() {
        JpaSession held = SkyBlockData.connect(new Checkout(LocalSkyBlockData.root()));

        assertThat(SkyBlockData.connect(), is(sameInstance(held)));
    }

}
