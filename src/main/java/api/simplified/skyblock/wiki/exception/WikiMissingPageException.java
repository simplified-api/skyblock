package api.simplified.skyblock.wiki.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a composite parse reaches a page, module or template the wiki does not hold, which renders as a red link.
 */
public final class WikiMissingPageException extends WikiException {

    /**
     * Constructs a new {@code WikiMissingPageException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public WikiMissingPageException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code WikiMissingPageException} with the given message.
     *
     * @param message the detail message
     */
    public WikiMissingPageException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code WikiMissingPageException} with the given cause and message.
     *
     * @param cause the underlying cause
     * @param message the detail message
     */
    public WikiMissingPageException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    /**
     * Constructs a new {@code WikiMissingPageException} with the given formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public WikiMissingPageException(@PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    /**
     * Constructs a new {@code WikiMissingPageException} with the given cause and formatted message.
     *
     * @param cause the underlying cause
     * @param message the format string
     * @param args the format arguments
     */
    public WikiMissingPageException(@NotNull Throwable cause, @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
