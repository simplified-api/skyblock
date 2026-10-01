/**
 * A Feign client for the Hypixel SkyBlock wiki, a MediaWiki site: rendered pages and page sources
 * under {@code /w/}, and the parse, query, search and Bucket modules of {@code api.php}.
 *
 * <p>
 * {@link api.simplified.skyblock.wiki.SkyBlockWikiContract SkyBlockWikiContract} is the contract and
 * the one type this package holds. Everything it sends, answers and raises lives beneath it:
 * <ul>
 *     <li><b>{@code client}</b> - {@link api.simplified.skyblock.wiki.client.SkyBlockWiki SkyBlockWiki}
 *     builds the client, with the {@code User-Agent} the wiki asks for, a text decoder for HTML and
 *     source, and error decoding into
 *     {@link api.simplified.skyblock.wiki.exception.WikiApiException WikiApiException}; a paged read
 *     waits out the client's rate limit before each of its parts, the first among them, through a
 *     {@link api.simplified.skyblock.wiki.client.WikiPacing WikiPacing}; and
 *     {@link api.simplified.skyblock.wiki.client.WikiResponseGuard WikiResponseGuard} runs the
 *     contract's checks on an answer fetched at a request's URL.</li>
 *     <li><b>{@code request}</b> - {@link api.simplified.skyblock.wiki.request.WikiRequest WikiRequest}
 *     defines every request shape once. The contract sends a request's parameter map, and a caller
 *     without the contract - a dataflow pipeline - fetches the same request at its URL, so a shape
 *     proven one way is proven the other. The wikitext a composite parse renders is composed by
 *     {@link api.simplified.skyblock.wiki.request.WikiText WikiText} and
 *     {@link api.simplified.skyblock.wiki.request.DplQuery DplQuery}, and a Bucket query by
 *     {@link api.simplified.skyblock.wiki.request.BucketQuery BucketQuery}.</li>
 *     <li><b>{@code response}</b> - the answers bind to
 *     {@link api.simplified.skyblock.wiki.response.WikiParse WikiParse},
 *     {@link api.simplified.skyblock.wiki.response.WikiQueryResult WikiQueryResult},
 *     {@link api.simplified.skyblock.wiki.response.WikiSearchResult WikiSearchResult} and
 *     {@link api.simplified.skyblock.wiki.response.WikiBucket WikiBucket}.</li>
 *     <li><b>{@code exception}</b> - the failures. The wiki reports six of them as successes, and
 *     each raises its own {@link api.simplified.skyblock.wiki.exception.WikiException WikiException}
 *     once the contract reads the answer.</li>
 * </ul>
 *
 * <p>
 * The six failures a status of 200 carries are a refusal written in place of the result
 * ({@link api.simplified.skyblock.wiki.exception.WikiErrorException WikiErrorException}), a composite
 * reaching a page or module that does not exist
 * ({@link api.simplified.skyblock.wiki.exception.WikiMissingPageException WikiMissingPageException}),
 * a composite cut short at the post-expand include size
 * ({@link api.simplified.skyblock.wiki.exception.WikiIncludeSizeException WikiIncludeSizeException}),
 * a Bucket answer, a DynamicPageList result or a search stopped at its limit
 * ({@link api.simplified.skyblock.wiki.exception.WikiLimitReachedException WikiLimitReachedException}),
 * a DynamicPageList result that wrote no filled footer to count its pages by
 * ({@link api.simplified.skyblock.wiki.exception.WikiMissingFooterException WikiMissingFooterException}),
 * and a redirect's stub read in place of a page's source
 * ({@link api.simplified.skyblock.wiki.exception.WikiRedirectException WikiRedirectException}).
 */
package api.simplified.skyblock.wiki;
