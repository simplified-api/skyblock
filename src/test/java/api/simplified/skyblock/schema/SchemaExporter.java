package api.simplified.skyblock.schema;

import api.simplified.skyblock.SkyBlockData;
import dev.simplified.gson.GsonSettings;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Exports H2 DDL from all embedded JPA sessions and creates a persistent H2 file
 * database for IntelliJ JPA column resolution.
 *
 * <p>Run via Gradle: {@code ./gradlew :minecraft-api:generateSchema}</p>
 *
 * <p>IntelliJ JDBC connection string:
 * {@code jdbc:h2:file:$PROJECT_DIR$/minecraft-api/.schema/<name>;ACCESS_MODE_DATA=r}
 * with user {@code sa} and an empty password.</p>
 */
public final class SchemaExporter {

    public static void main(String @NotNull [] args) {
        Path outputDir = Path.of(args.length > 0 ? args[0] : ".schema").normalize().toAbsolutePath();

        // Triggers MinecraftApi static init, which builds all H2 sessions.
        // Shutdown is handled by JVM shutdown hooks in SessionManager and Scheduler.
        SkyBlockData.connect(GsonSettings.defaults());
        SkyBlockData.getSessionManager()
            .getSessions()
            .stream()
            .filter(session -> session.getConfig().getDriver().isEmbedded())
            .forEach(session -> session.exportSchema(outputDir));
    }

}
