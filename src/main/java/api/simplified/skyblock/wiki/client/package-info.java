/**
 * The client {@link api.simplified.skyblock.wiki.SkyBlockWikiContract SkyBlockWikiContract} sends
 * through, how a paged read paces itself against it, and the contract's checks for an answer another
 * client fetched.
 *
 * <p>
 * {@link api.simplified.skyblock.wiki.client.SkyBlockWiki SkyBlockWiki} builds the client: a
 * descriptive {@code User-Agent}, a
 * {@link api.simplified.skyblock.wiki.client.WikiDecoder WikiDecoder} that reads a {@code String}
 * answer as the body's text and hands every other type to the JSON decoder, and error decoding into
 * {@link api.simplified.skyblock.wiki.exception.WikiApiException WikiApiException}.
 * {@link api.simplified.skyblock.wiki.client.WikiPacing WikiPacing} waits out the client's rate limit
 * before each part of a paged read and sends the part again until the limiter lets it through.
 *
 * <p>
 * {@link api.simplified.skyblock.wiki.client.WikiResponseGuard WikiResponseGuard} reads a request's
 * shape from the URL a dataflow pipeline fetched and runs the checks the contract runs on the same
 * request, so an answer read without the contract fails the way the contract would fail it.
 */
package api.simplified.skyblock.wiki.client;
