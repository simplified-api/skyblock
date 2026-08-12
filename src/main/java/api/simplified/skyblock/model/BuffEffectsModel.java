package api.simplified.skyblock.model;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The shared contract for a reference row that carries a stat payload - a flat set of numeric stat
 * additions plus a looser set of conditional effects.
 */
public interface BuffEffectsModel {

    /**
     * Flat stat additions the row grants, keyed by {@link Stat} id.
     */
    @NotNull Map<String, Double> getEffects();

    /**
     * Conditional or non-numeric effects keyed by name, typed loosely because the payload shape
     * differs per effect.
     */
    @NotNull Map<String, Object> getBuffEffects();

    /**
     * Reads one flat stat addition, treating an absent stat as no contribution.
     *
     * @param key the {@link Stat} id to read
     * @return the amount granted, or zero when the row does not touch that stat
     */
    default double getEffect(@NotNull String key) {
        return getEffect(key, 0.0);
    }

    /**
     * Reads one flat stat addition, falling back to a caller-chosen amount.
     *
     * @param key the {@link Stat} id to read
     * @param defaultValue the amount to answer with when the row does not touch that stat
     * @return the amount granted, or {@code defaultValue}
     */
    default double getEffect(@NotNull String key, double defaultValue) {
        return getEffects().getOrDefault(key, defaultValue);
    }

}
