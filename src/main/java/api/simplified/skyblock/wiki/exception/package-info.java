/**
 * The failures {@link api.simplified.skyblock.wiki.SkyBlockWikiContract SkyBlockWikiContract}
 * raises.
 *
 * <p>
 * An error status is the client's:
 * {@link api.simplified.skyblock.wiki.exception.WikiApiException WikiApiException} carries any status
 * of 400 or above the client does not raise itself, with the body read into a
 * {@link api.simplified.skyblock.wiki.exception.WikiErrorResponse WikiErrorResponse} when it is a
 * MediaWiki error envelope.
 *
 * <p>
 * The wiki reports six failures with a status of 200, and each is a
 * {@link api.simplified.skyblock.wiki.exception.WikiException WikiException} raised once the answer
 * is read: a refusal written in place of the result
 * ({@link api.simplified.skyblock.wiki.exception.WikiErrorException WikiErrorException}), a composite
 * reaching a page or module that does not exist
 * ({@link api.simplified.skyblock.wiki.exception.WikiMissingPageException WikiMissingPageException}),
 * a composite cut short at the post-expand include size
 * ({@link api.simplified.skyblock.wiki.exception.WikiIncludeSizeException WikiIncludeSizeException}),
 * a result stopped at its limit
 * ({@link api.simplified.skyblock.wiki.exception.WikiLimitReachedException WikiLimitReachedException}),
 * a DynamicPageList result without the counts its footer carries
 * ({@link api.simplified.skyblock.wiki.exception.WikiMissingFooterException WikiMissingFooterException}),
 * and a redirect's stub read in place of a page's source
 * ({@link api.simplified.skyblock.wiki.exception.WikiRedirectException WikiRedirectException}).
 */
package api.simplified.skyblock.wiki.exception;
