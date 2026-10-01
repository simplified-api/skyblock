package api.simplified.skyblock.wiki.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the wiki answers a request with a refusal in place of its result.
 */
public final class WikiErrorException extends WikiException {

    /**
     * Constructs a new {@code WikiErrorException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public WikiErrorException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code WikiErrorException} with the given message.
     *
     * @param message the detail message
     */
    public WikiErrorException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code WikiErrorException} with the given cause and message.
     *
     * @param cause the underlying cause
     * @param message the detail message
     */
    public WikiErrorException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    /**
     * Constructs a new {@code WikiErrorException} with the given formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public WikiErrorException(@PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    /**
     * Constructs a new {@code WikiErrorException} with the given cause and formatted message.
     *
     * @param cause the underlying cause
     * @param message the format string
     * @param args the format arguments
     */
    public WikiErrorException(@NotNull Throwable cause, @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
