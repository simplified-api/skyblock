package dev.sbs.skyblockdata;

import dev.sbs.skyblockdata.date.SkyBlockDate;
import dev.simplified.gson.GsonContributor;
import dev.simplified.gson.GsonSettings;
import dev.simplified.persistence.JpaExclusionStrategy;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the SkyBlock-specific type adapters and the JPA exclusion strategy
 * with {@link GsonSettings#defaults()}.
 * <p>
 * Discovered via the {@link java.util.ServiceLoader} entry at
 * {@code META-INF/services/dev.simplified.gson.GsonContributor} whenever this
 * module is on the classpath; consumers of {@code GsonSettings.defaults()} get
 * the adapters automatically without touching their own bootstrap code.
 */
public final class SkyBlockDataGsonContributor implements GsonContributor {

    @Override
    public void contribute(GsonSettings.@NotNull Builder builder) {
        builder
            .withTypeAdapter(SkyBlockDate.RealTime.class, new SkyBlockDate.RealTime.Adapter())
            .withTypeAdapter(SkyBlockDate.SkyBlockTime.class, new SkyBlockDate.SkyBlockTime.Adapter())
            .withExclusionStrategies(JpaExclusionStrategy.INSTANCE);
    }

    /**
     * Applied after default-priority contributors so the {@link JpaExclusionStrategy}
     * sees the full registered type-adapter set when Gson wires the Hibernate JSON columns.
     */
    @Override
    public int priority() {
        return 100;
    }

}
