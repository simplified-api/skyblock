package api.simplified.skyblock.wiki.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a composite parse outgrows the wiki's post-expand include size and the wiki leaves out what does not fit.
 */
public final class WikiIncludeSizeException extends WikiException {

    /**
     * Constructs a new {@code WikiIncludeSizeException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public WikiIncludeSizeException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code WikiIncludeSizeException} with the given message.
     *
     * @param message the detail message
     */
    public WikiIncludeSizeException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code WikiIncludeSizeException} with the given cause and message.
     *
     * @param cause the underlying cause
     * @param message the detail message
     */
    public WikiIncludeSizeException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    /**
     * Constructs a new {@code WikiIncludeSizeException} with the given formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public WikiIncludeSizeException(@PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    /**
     * Constructs a new {@code WikiIncludeSizeException} with the given cause and formatted message.
     *
     * @param cause the underlying cause
     * @param message the format string
     * @param args the format arguments
     */
    public WikiIncludeSizeException(@NotNull Throwable cause, @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
