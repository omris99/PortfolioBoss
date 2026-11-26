package portfolioboss.recommendationlists;

import java.util.List;

/**
 * What a sync asks IB for, for the Recommendations page's lists (RECOMMENDATIONS_FINVIZ_TODO.md, session 2): the symbols
 * of the latest download whose contracts haven't been found yet, and the contracts already known — the daily closes of
 * both are read, once a trading day. {@link #NOTHING} before the first download, and when the closes were read after
 * the latest US market close.
 *
 * @param downloadId           the download the stocks belong to; {@code null} for nothing to read
 * @param symbolsToFind        as IB writes them: "BRK B"
 * @param conIdsToReadClosesOf the contracts found before, by this download or an earlier one
 */
public record ListedStocksToRead(Long downloadId, List<String> symbolsToFind, List<Integer> conIdsToReadClosesOf) {

    public static final ListedStocksToRead NOTHING = new ListedStocksToRead(null, List.of(), List.of());

    public boolean isNothing() {
        return downloadId == null;
    }
}
