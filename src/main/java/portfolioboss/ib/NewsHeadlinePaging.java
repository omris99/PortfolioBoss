package portfolioboss.ib;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Dow Jones headlines of each scanned stock, read page by page back in time (RECOMMENDATIONS_TODO.md, decision 14):
 * IB sends at most 300 for one request, and a busy stock has over a thousand in half a year. Each round asks every stock
 * still being read for the page before the oldest headline it has. A stock is done when its page was not full (there are
 * no more), brought nothing new, came back empty (the request failed or came late), or reached as far back as the
 * targets count.
 */
final class NewsHeadlinePaging {

    /** The most headlines IB sends for one request. */
    protected static final int HEADLINES_PER_PAGE = 300;

    private final LocalDateTime oldestWanted;
    /** By contract id, the headlines read so far, by IB's article id: a headline on two pages is kept once. */
    private final Map<Integer, Map<String, NewsHeadline>> headlinesByConId = new LinkedHashMap<>();
    /**
     * The stocks still being read, each with the time its next page must be older than — {@code null} for the first
     * page, the newest.
     */
    private final Map<Integer, LocalDateTime> nextPageOlderThanByConId = new LinkedHashMap<>();

    /**
     * @param oldestWanted a stock whose page goes back this far is done: older headlines are no longer counted
     */
    protected NewsHeadlinePaging(Collection<Integer> conIds, LocalDateTime oldestWanted) {
        this.oldestWanted = oldestWanted;
        for (int conId : conIds) {
            headlinesByConId.put(conId, new LinkedHashMap<>());
            nextPageOlderThanByConId.put(conId, null);
        }
    }

    protected boolean hasStocksToRead() {
        return !nextPageOlderThanByConId.isEmpty();
    }

    /** The next round: each stock still being read, with the time its page must be older than — {@code null}: newest. */
    protected Map<Integer, LocalDateTime> nextPages() {
        return new LinkedHashMap<>(nextPageOlderThanByConId);
    }

    /** One stock's page of this round; an empty one ends its reading with what it has. */
    protected void addPage(int conId, List<NewsHeadline> page) {
        Map<String, NewsHeadline> headlines = headlinesByConId.get(conId);
        int headlineCountBefore = headlines.size();
        page.forEach(newsHeadline -> headlines.putIfAbsent(newsHeadline.articleId(), newsHeadline));

        Optional<LocalDateTime> oldestOnPage = page.stream()
                .map(NewsHeadline::publishedAt)
                .min(Comparator.naturalOrder());
        boolean pageWasFull = page.size() >= HEADLINES_PER_PAGE;
        boolean broughtSomethingNew = headlines.size() > headlineCountBefore;
        boolean reachedOldestWanted = oldestOnPage.isPresent() && !oldestOnPage.get().isAfter(oldestWanted);
        if (pageWasFull && broughtSomethingNew && !reachedOldestWanted) {
            nextPageOlderThanByConId.put(conId, oldestOnPage.get());
        } else {
            nextPageOlderThanByConId.remove(conId);
        }
    }

    /** By contract id, every headline read of each stock that has at least one. */
    protected Map<Integer, List<NewsHeadline>> headlinesByConId() {
        Map<Integer, List<NewsHeadline>> headlinesReadByConId = new LinkedHashMap<>();
        headlinesByConId.forEach((conId, headlines) -> {
            if (!headlines.isEmpty()) {
                headlinesReadByConId.put(conId, List.copyOf(headlines.values()));
            }
        });
        return headlinesReadByConId;
    }
}
