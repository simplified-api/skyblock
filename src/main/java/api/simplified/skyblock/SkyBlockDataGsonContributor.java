package api.simplified.skyblock;

import api.simplified.skyblock.date.SkyBlockDate;
import dev.simplified.gson.GsonContributor;
import dev.simplified.gson.GsonSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ServiceLoader;

/**
 * Registers the SkyBlock-specific type adapters with {@link GsonSettings#defaults()}.
 * <p>
 * Discovered via the {@link ServiceLoader} entry at
 * {@code META-INF/services/dev.simplified.gson.GsonContributor} whenever this
 * module is on the classpath; consumers of {@code GsonSettings.defaults()} get
 * the adapters automatically without touching their own bootstrap code.
 */
public final class SkyBlockDataGsonContributor implements GsonContributor {

    /** {@inheritDoc} */
    @Override
    public void contribute(GsonSettings.@NotNull Builder builder) {
        builder
            .withTypeAdapter(SkyBlockDate.RealTime.class, new SkyBlockDate.RealTime.Adapter())
            .withTypeAdapter(SkyBlockDate.SkyBlockTime.class, new SkyBlockDate.SkyBlockTime.Adapter());
    }

}
