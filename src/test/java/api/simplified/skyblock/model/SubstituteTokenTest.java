package api.simplified.skyblock.model;

import api.simplified.skyblock.LocalSkyBlockData;
import api.simplified.skyblock.SkyBlockData;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.JpaSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins that a description and the substitutes filling it name the same things.
 * <p>
 * A description is a template and a {@code %{VALUE:X}} token is filled by the substitute whose id is
 * {@code X}, so the id is the token's key rather than a note about what the row grants. The two
 * halves are authored separately and nothing but this compares them, which is how an enchantment
 * came to promise Health through a substitute named for projectile defence: the token resolved to
 * nothing, the id resolved to no stat, and every level of Growth and Protection granted zero in
 * silence.
 * <p>
 * Both directions are checked, because each catches a different slip. An unnamed token is a number
 * missing from a rendered line; an unnamed substitute is a grant that never lands. The only id
 * exempt is {@code DEFAULT}, which is what a row carries when its number names no stat at all - a
 * category-wide percentage, a radius, a duration.
 * <p>
 * This says nothing about whether an id resolves to a {@link Stat}. Most do not and should not:
 * a cap in players, a distance in blocks and a cooldown in seconds are all real substitutes that no
 * stat corresponds to.
 */
class SubstituteTokenTest {

    /**
     * The interpolation this checks. Other prefixes - {@code STAT}, {@code KEYWORD}, {@code ZONE} -
     * name a row in another table and are not filled from the substitute list.
     */
    private static final @NotNull Pattern VALUE_TOKEN = Pattern.compile("%\\{VALUE:([^}]*)}");

    /**
     * The id a substitute carries when the description interpolates a number naming no stat.
     */
    private static final @NotNull String FILLER = "DEFAULT";

    private static JpaSession session;

    @BeforeAll
    static void loadCorpus() {
        Path corpus = LocalSkyBlockData.root();

        ConcurrentList<String> uncovered = LocalSkyBlockData.uncoveredModels(corpus);
        assumeTrue(uncovered.isEmpty(), "the models and the manifest are of different vintages - the manifest carries no file for " + uncovered);

        session = LocalSkyBlockData.connect(corpus);
    }

    @AfterAll
    static void releaseSession() {
        LocalSkyBlockData.disconnect(session);
        session = null;
    }

    @Test
    @DisplayName("every enchantment description and its substitutes name the same things")
    void everyEnchantmentAgreesWithItsSubstitutes() {
        ConcurrentList<Enchantment> rows = SkyBlockData.getRepository(Enchantment.class).findAll();
        assertThat(rows.size(), is(greaterThan(0)));

        ConcurrentList<String> refusals = Concurrent.newList();

        rows.forEach(row -> check(
            "enchantment '" + row.getId() + "'",
            row.getDescription(),
            row.getStats().stream().map(Stat.Substitute::getId).collect(Concurrent.toList()),
            refusals
        ));

        assertThat(refusals, is(empty()));
    }

    @Test
    @DisplayName("every pet perk description and its substitutes name the same things")
    void everyPetPerkAgreesWithItsSubstitutes() {
        ConcurrentList<Pet> rows = SkyBlockData.getRepository(Pet.class).findAll();
        assertThat(rows.size(), is(greaterThan(0)));

        ConcurrentList<String> refusals = Concurrent.newList();

        rows.forEach(row -> row.getPerks().forEach(perk -> check(
            "pet '" + row.getId() + "' perk '" + perk.getName() + "'",
            perk.getDescription(),
            perk.getStats().stream().map(Pet.Substitute::getId).collect(Concurrent.toList()),
            refusals
        )));

        assertThat(refusals, is(empty()));
    }

    @Test
    @DisplayName("a pet's own stats name a stat that exists, because those are granted rather than rendered")
    void everyPetStatResolves() {
        ConcurrentList<Pet> rows = SkyBlockData.getRepository(Pet.class).findAll();
        ConcurrentList<String> refusals = Concurrent.newList();

        // a perk substitute may name anything the description interpolates, but a pet's own stat list
        // is read straight into the sheet - an id that resolves to nothing there is a grant that
        // silently never happens
        rows.forEach(row -> row.getStats()
            .stream()
            .filter(substitute -> substitute.getStat().isEmpty())
            .forEach(substitute -> refusals.add(String.format("pet '%s' grants '%s', which no stat carries", row.getId(), substitute.getId())))
        );

        assertThat(refusals, is(empty()));
    }

    private static void check(@NotNull String subject, @NotNull String description, @NotNull ConcurrentList<String> ids, @NotNull ConcurrentList<String> refusals) {
        ConcurrentList<String> tokens = Concurrent.newList();
        Matcher matcher = VALUE_TOKEN.matcher(description);

        while (matcher.find())
            tokens.add(matcher.group(1));

        tokens.stream()
            .filter(token -> !ids.contains(token))
            .forEach(token -> refusals.add(String.format("%s interpolates '%s', which no substitute is named for", subject, token)));

        ids.stream()
            .filter(id -> !FILLER.equals(id))
            .filter(id -> !tokens.contains(id))
            .forEach(id -> refusals.add(String.format("%s carries a substitute named '%s', which its description never interpolates", subject, id)));
    }

}
