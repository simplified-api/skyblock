/**
 * The answers {@link api.simplified.skyblock.wiki.SkyBlockWikiContract SkyBlockWikiContract} binds.
 *
 * <p>
 * Every {@code api.php} answer is a
 * {@link api.simplified.skyblock.wiki.response.WikiResponse WikiResponse}, which carries either its
 * result or the {@link api.simplified.skyblock.wiki.response.WikiError WikiError} the wiki wrote in
 * its place with a status of 200. A parse binds to
 * {@link api.simplified.skyblock.wiki.response.WikiParse WikiParse}, a query to
 * {@link api.simplified.skyblock.wiki.response.WikiQueryResult WikiQueryResult}, a search to
 * {@link api.simplified.skyblock.wiki.response.WikiSearchResult WikiSearchResult} and a Bucket query
 * to {@link api.simplified.skyblock.wiki.response.WikiBucket WikiBucket}; a redirect the wiki
 * followed to answer is named by a
 * {@link api.simplified.skyblock.wiki.response.WikiRedirect WikiRedirect}.
 *
 * <p>
 * Each answer carries the checks for the failures the wiki reports as successes, which the contract
 * runs on every answer it reads:
 * {@link api.simplified.skyblock.wiki.response.WikiResponse#requireSuccess() WikiResponse.requireSuccess},
 * {@link api.simplified.skyblock.wiki.response.WikiParse#requireComplete() WikiParse.requireComplete},
 * {@link api.simplified.skyblock.wiki.response.WikiParse#requireTableRows(int) WikiParse.requireTableRows},
 * {@link api.simplified.skyblock.wiki.response.WikiSearchResult#requireReachable() WikiSearchResult.requireReachable},
 * {@link api.simplified.skyblock.wiki.response.WikiQueryResult#requireReachable() WikiQueryResult.requireReachable}
 * and
 * {@link api.simplified.skyblock.wiki.response.WikiBucket#requireUnderLimit(int) WikiBucket.requireUnderLimit}.
 */
package api.simplified.skyblock.wiki.response;
