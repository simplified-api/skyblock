package api.simplified.skyblock.wiki.exception;

import api.simplified.skyblock.wiki.request.DplQuery;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a {@link DplQuery} result writes no footer counting its pages, or one the wiki left
 * unfilled, so whether it stopped short of the pages its query matches cannot be read.
 */
public final class WikiMissingFooterException extends WikiException {

    /**
     * Constructs a new {@code WikiMissingFooterException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public WikiMissingFooterException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code WikiMissingFooterException} with the given message.
     *
     * @param message the detail message
     */
    public WikiMissingFooterException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code WikiMissingFooterException} with the given cause and message.
     *
     * @param cause the underlying cause
     * @param message the detail message
     */
    public WikiMissingFooterException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    /**
     * Constructs a new {@code WikiMissingFooterException} with the given formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public WikiMissingFooterException(@PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    /**
     * Constructs a new {@code WikiMissingFooterException} with the given cause and formatted message.
     *
     * @param cause the underlying cause
     * @param message the format string
     * @param args the format arguments
     */
    public WikiMissingFooterException(@NotNull Throwable cause, @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
