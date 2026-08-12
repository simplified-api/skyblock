package api.simplified.skyblock.model;

import com.google.gson.Gson;
import dev.simplified.gson.GsonSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Covers the experience ladders, whose thresholds carry a wire name that differs from the field.
 */
class LadderBindingTest {

    private static final String SKILL = """
        {
          "id": "FARMING",
          "name": "Farming",
          "maxLevel": 60,
          "levels": [
            { "level": 1, "totalExpRequired": 50 },
            { "level": 2, "totalExpRequired": 175 },
            { "level": 3, "totalExpRequired": 410 }
          ]
        }
        """;

    private static final String SLAYER = """
        {
          "id": "ZOMBIE",
          "name": "Revenant Horror",
          "levels": [
            { "level": 1, "totalExpRequired": 5 },
            { "level": 2, "totalExpRequired": 15 },
            { "level": 3, "totalExpRequired": 200 }
          ]
        }
        """;

    private static Gson gson() {
        return GsonSettings.defaults().create();
    }

    @Test
    @DisplayName("a skill ladder binds the thresholds the data spells totalExpRequired")
    void skillLadderBindsThresholds() {
        Skill skill = gson().fromJson(SKILL, Skill.class);

        // the field is named totalRequiredXP while the data spells the key totalExpRequired, and no
        // field naming policy is configured - without the explicit name every threshold binds zero
        assertThat(skill.getExperienceTiers(), contains(50, 175, 410));
    }

    @Test
    @DisplayName("a slayer ladder binds the thresholds the data spells totalExpRequired")
    void slayerLadderBindsThresholds() {
        Slayer slayer = gson().fromJson(SLAYER, Slayer.class);

        assertThat(slayer.getExperienceTiers(), contains(5, 15, 200));
    }

    @Test
    @DisplayName("a zeroed ladder would report every level as maxed")
    void zeroedLadderReportsEveryLevelMaxed() {
        Skill skill = gson().fromJson(SKILL, Skill.class);

        // a threshold of zero never exceeds an experience total, so the level scan falls through to
        // the cap - which is why a silent binding failure here reads as a maxed skill rather than as
        // a missing one
        assertThat(skill.getExperienceTiers().getFirst(), is(not(equalTo(0))));
    }

}
