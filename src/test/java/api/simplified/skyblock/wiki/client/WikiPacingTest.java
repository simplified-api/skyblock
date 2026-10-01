package api.simplified.skyblock.wiki.client;

import dev.simplified.client.ratelimit.RateLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers how long a paged read waits after a refusal, read off the refused policy alone; the waits a
 * paged read makes through the contract are covered by the contract's suite.
 */
class WikiPacingTest {

    @Test
    @DisplayName("waits one request's share of the policy's window when it names no reset")
    void share() {
        Instant now = Instant.ofEpochMilli(1_000_000L);

        assertThat(WikiPacing.waitAfter(new RateLimit(60, 60, ChronoUnit.SECONDS), now), equalTo(Duration.ofSeconds(1)));
        assertThat(WikiPacing.waitAfter(new RateLimit(240, 60, ChronoUnit.SECONDS), now), equalTo(Duration.ofMillis(250)));
        assertThrows(IllegalArgumentException.class, () -> WikiPacing.of(() -> now, duration -> {}, Duration.ofSeconds(-1)));
    }

}
