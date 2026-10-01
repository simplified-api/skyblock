package api.simplified.skyblock.wiki.exception;

import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when a read of a page's source answers a redirect's stub - {@code #REDIRECT [[Target]]}, or
 * a module's {@code return require [[Module:Target]]} - in place of the source of the page it names.
 */
public final class WikiRedirectException extends WikiException {

    /**
     * Constructs a new {@code WikiRedirectException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public WikiRedirectException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code WikiRedirectException} with the given message.
     *
     * @param message the detail message
     */
    public WikiRedirectException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code WikiRedirectException} with the given cause and message.
     *
     * @param cause the underlying cause
     * @param message the detail message
     */
    public WikiRedirectException(@NotNull Throwable cause, @NotNull String message) {
        super(cause, message);
    }

    /**
     * Constructs a new {@code WikiRedirectException} with the given formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public WikiRedirectException(@PrintFormat String message, @Nullable Object... args) {
        super(message, args);
    }

    /**
     * Constructs a new {@code WikiRedirectException} with the given cause and formatted message.
     *
     * @param cause the underlying cause
     * @param message the format string
     * @param args the format arguments
     */
    public WikiRedirectException(@NotNull Throwable cause, @PrintFormat String message, @Nullable Object... args) {
        super(cause, message, args);
    }

}
