package api.simplified.skyblock.model;

import api.simplified.skyblock.LocalSkyBlockData;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.persistence.JpaSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Asserts that a reward line grants the stat it names and no other.
 *
 * <p>Every stat name that nests inside a longer one was being credited alongside it, because the
 * scrape asked all 88 stats whether the line ended in their name instead of asking the line which
 * stat it spelled. A line reading {@code Ability Damage} granted {@code Damage} too, and while the
 * Rift stats shared a display name with their overworld twins an Enchanting level granted
 * {@code Rift Intelligence}.
 */
class StatGrantsTest {

    private static JpaSession session;

    @BeforeAll
    static void connectSession() {
        session = LocalSkyBlockData.connect(LocalSkyBlockData.root());
    }

    @AfterAll
    static void releaseSession() {
        LocalSkyBlockData.disconnect(session);
        session = null;
    }

    @Test
    void aLineGrantsTheLongestStatItNamesAndNotTheOnesNestedInside() {
        ConcurrentMap<String, Double> granted = level("ENCHANTING", 1);

        // '+0.5% Ability Damage' and '+1 Intelligence', and nothing else on the level names a stat.
        assertEquals(0.5, granted.get("ABILITY_DAMAGE"));
        assertFalse(granted.containsKey("DAMAGE"), () -> "Ability Damage is not Damage: " + granted);

        assertEquals(1.0, granted.get("INTELLIGENCE"));
        assertFalse(granted.containsKey("RIFT_INTELLIGENCE"), () -> "the Rift is a dimension of its own: " + granted);
    }

    @Test
    void aRiftLineStillGrantsItsRiftStat() {
        // The control for the one above. A slayer whose rewards really are Rift rewards has to keep
        // them, so 'exclude the Rift stats' would have been the wrong fix.
        assertEquals(20.0, level("VAMPIRE", 1).get("RIFT_TIME"));
    }

    @Test
    void aLevelResolvesItsGrantsOnce() {
        Skill.Level level = session.getRepository(Skill.class).orElseThrow()
            .findFirst(Skill::getId, "ENCHANTING")
            .orElseThrow()
            .getLevels()
            .getFirst();

        assertSame(level.getEffects(), level.getEffects(), "a level is rebuilt per generation, so what it resolved is held");
    }

    private static ConcurrentMap<String, Double> level(String skillOrSlayer, int level) {
        if ("VAMPIRE".equals(skillOrSlayer)) {
            return session.getRepository(Slayer.class).orElseThrow()
                .findFirst(Slayer::getId, skillOrSlayer)
                .orElseThrow()
                .getLevels()
                .findFirst(Slayer.Level::getLevel, level)
                .orElseThrow()
                .getEffects();
        }

        return session.getRepository(Skill.class).orElseThrow()
            .findFirst(Skill::getId, skillOrSlayer)
            .orElseThrow()
            .getLevels()
            .findFirst(Skill.Level::getLevel, level)
            .orElseThrow()
            .getEffects();
    }

}
