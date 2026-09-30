package api.simplified.skyblock.wiki.exception;

import api.simplified.skyblock.wiki.request.DplQuery;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when an answer stops at a limit short of what its request matches - a Bucket answer holding
 * as many rows as its query's limit, a {@link DplQuery} result holding fewer pages than its query
 * matches, or a search matching more pages than the wiki serves hits for - so what the request
 * matches beyond that limit is missing from it.
 */
public final class WikiLimitReachedException extends WikiException {

    /**
     * Constructs a new {@code WikiLimitReachedException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public WikiLimitReachedException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code WikiLimitReachedException} with the given message.
     *
     * @param message the detail message
     */
    public WikiLimitReachedException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code WikiLimitReachedException} with the given cause and message.
     *
     * @param cause the underlying cause
     * @param message the detail message
     */
    public WikiLimitReachedException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    /**
     * Constructs a new {@code WikiLimitReachedException} with the given formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public WikiLimitReachedException(@PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    /**
     * Constructs a new {@code WikiLimitReachedException} with the given cause and formatted message.
     *
     * @param cause the underlying cause
     * @param message the format string
     * @param args the format arguments
     */
    public WikiLimitReachedException(@NotNull Throwable cause, @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
