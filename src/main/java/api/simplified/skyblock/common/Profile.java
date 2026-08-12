package api.simplified.skyblock.common;

import com.google.gson.annotations.SerializedName;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.StreamUtil;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.util.StringUtil;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The names a profile can be given, each shown in game with its own symbol.
 * <p>
 * Hypixel names a new profile after a fruit, and collecting one profile of every fruit is what fills
 * the fruit bowl. The handful of constants below the fruits are not part of that set - they are names
 * that reached live profiles by other routes, and each is spelled by the wire rather than derived
 * from the constant.
 *
 * @see <a href="https://hypixelskyblock.minecraft.wiki/w/Fruit_Bowl">Fruit Bowl</a>
 */
@Getter
@RequiredArgsConstructor
public enum Profile {

    /**
     * One of the fruit names a new profile can be given.
     */
    APPLE("🍎", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    BANANA("🍌", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    BLUEBERRY("🫐", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    COCONUT("🥥", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    CUCUMBER("🥒", true),

    /**
     * One of the fruit names a new profile can be given, and the only plural among them.
     */
    GRAPES("🍇", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    KIWI("🥝", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    LEMON("🍋", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    LIME("\uD83C\uDF4B\u200D\uD83D\uDFE9", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    MANGO("🥭", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    ORANGE("🍊", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    PAPAYA("🍈", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    PEACH("🍑", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    PEAR("🍐", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    PINEAPPLE("🍍", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    POMEGRANATE("🌰", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    RASPBERRY("🍒", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    STRAWBERRY("🍓", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    TOMATO("🍅", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    WATERMELON("🍉", true),

    /**
     * One of the fruit names a new profile can be given.
     */
    ZUCCHINI("🍆", true),

    // Heatran

    /**
     * A one-off profile name carried by a live profile, spelled by the wire and outside the fruit set.
     */
    @SerializedName("Complain Everyday")
    COMPLAIN_EVERYDAY("💢", false),

    // Linfoot

    /**
     * A one-off profile name carried by a live profile, spelled by the wire and outside the fruit set.
     */
    @SerializedName("TEST")
    TEST("💢", false),

    // Restored Profiles

    /**
     * The name given to a profile brought back by support, rather than one chosen by its owner.
     */
    @SerializedName("Restored")
    RESTORED("🗑", false),

    // Akinsoft

    /**
     * A one-off profile name carried by a live profile, spelled by the wire and outside the fruit set.
     */
    @SerializedName("Not Allowed To Quit Skyblock Ever Again")
    NOT_ALLOWED_TO_QUIT_SKYBLOCK_EVER_AGAIN("💢", false);

    /**
     * Emoji the game shows beside the profile's name.
     */
    private final @NotNull String symbol;

    /**
     * Whether owning a profile of this name counts toward the fruit bowl.
     */
    private final boolean usedForFruitBowl;

    /**
     * Every profile name paired with its display form, for offering the set as a choice.
     */
    public final static @NotNull ConcurrentMap<String, Object> CHOICES = StreamUtil.ofArrays(values())
        .map(profile -> Pair.of(profile.name(), profile.getName()))
        .collect(Concurrent.toWeakUnmodifiableMap());

    /**
     * The profile name in the capitalisation the game displays.
     */
    public @NotNull String getName() {
        return StringUtil.capitalizeFully(this.name().replace("_", " "));
    }

    /**
     * Finds the profile name matching a given string, ignoring case.
     *
     * @param name the profile name to match
     * @return the matching name, empty when none does
     */
    public static @NotNull Optional<Profile> of(@NotNull String name) {
        return StreamUtil.ofArrays(values())
            .filter(profile -> profile.name().equalsIgnoreCase(name))
            .findFirst();
    }

}
