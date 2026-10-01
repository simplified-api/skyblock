/**
 * The requests {@link api.simplified.skyblock.wiki.SkyBlockWikiContract SkyBlockWikiContract} sends.
 *
 * <p>
 * {@link api.simplified.skyblock.wiki.request.WikiRequest WikiRequest} defines every request shape
 * once, as the parameter map the contract sends and as the URL a caller without the contract fetches;
 * a request whose URL would outgrow
 * {@link api.simplified.skyblock.wiki.request.WikiRequest#MAX_URL_LENGTH MAX_URL_LENGTH} characters is
 * refused. The wikitext a composite parse renders is composed by
 * {@link api.simplified.skyblock.wiki.request.WikiText WikiText}, one part per document, and
 * {@link api.simplified.skyblock.wiki.request.DplQuery DplQuery}, a DynamicPageList query over the
 * pages that use one template; a Bucket query is composed by
 * {@link api.simplified.skyblock.wiki.request.BucketQuery BucketQuery}. A Bucket query renders its
 * limit and a DynamicPageList query a footer counting its pages, so an answer stopped at its limit
 * can be told from a complete one.
 */
package api.simplified.skyblock.wiki.request;
