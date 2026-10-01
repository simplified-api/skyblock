package api.simplified.skyblock.wiki.exception;

import api.simplified.skyblock.wiki.SkyBlockWikiContract;
import api.simplified.skyblock.wiki.client.WikiResponseGuard;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when the Hypixel SkyBlock wiki answers a request with an HTTP status of 200 and something
 * other than what the request asked for.
 *
 * <p>
 * A failure with an error status is a {@link WikiApiException}, which the client raises; every
 * failure the wiki reports as a success is one of these, raised by {@link SkyBlockWikiContract} once
 * it reads the answer, or by {@link WikiResponseGuard} for an answer fetched without it.
 */
public class WikiException extends RuntimeException {

    /**
     * Constructs a new {@code WikiException} with the given cause.
     *
     * @param cause the underlying cause
     */
    public WikiException(@NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code WikiException} with the given message.
     *
     * @param message the detail message
     */
    public WikiException(@NotNull String message) {
        super(message);
    }

    /**
     * Constructs a new {@code WikiException} with the given cause and message.
     *
     * @param cause the underlying cause
     * @param message the detail message
     */
    public WikiException(@NotNull Throwable cause, @NotNull String message) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code WikiException} with the given formatted message.
     *
     * @param message the format string
     * @param args the format arguments
     */
    public WikiException(@PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args));
    }

    /**
     * Constructs a new {@code WikiException} with the given cause and formatted message.
     *
     * @param cause the underlying cause
     * @param message the format string
     * @param args the format arguments
     */
    public WikiException(@NotNull Throwable cause, @PrintFormat String message, @Nullable Object... args) {
        super(String.format(message, args), cause);
    }

}
