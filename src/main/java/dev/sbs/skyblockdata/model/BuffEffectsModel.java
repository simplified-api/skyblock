package dev.sbs.minecraftapi.persistence.model;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Models that provide stat effects and conditional buff effects.
 */
public interface BuffEffectsModel {

    @NotNull Map<String, Double> getEffects();

    @NotNull Map<String, Object> getBuffEffects();

    default double getEffect(@NotNull String key) {
        return getEffect(key, 0.0);
    }

    default double getEffect(@NotNull String key, double defaultValue) {
        return getEffects().getOrDefault(key, defaultValue);
    }

}
