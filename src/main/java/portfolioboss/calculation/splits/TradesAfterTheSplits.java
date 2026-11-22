package portfolioboss.calculation.splits;

import portfolioboss.calculation.portfolio.HoldingHistory;
import portfolioboss.calculation.portfolio.TradeFact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The trades typed in for a holding, as they count after its splits (STOCK_SPLITS_TODO.md, decisions 1 and 7). A split
 * counts only the shares held at it, so of each investor only the trades of the open position at the split — from the
 * last time they held none of the stock, up to the split's day ({@link HoldingHistory#openPositionTrades}) — are
 * translated: the quantity times the split, the price divided by it, the commission as it was. A position sold in full
 * before the split stays as it happened, whether or not the stock was bought again later: IB reports no split of a stock
 * no longer held.
 *
 * <p>Example: 10 shares bought at $400, then a split of 4 for 1: the buy counts as 40 at $100 — the same cost — and IB's
 * 40 shares match. Had the 10 been sold in full before the split, nothing would be translated.
 *
 * <p>Worked out on every read: the stored trades stay as they were typed, as on IB's confirmation of the day.
 *
 * @param tradesAsTyped      every trade typed in for the holding, of every investor
 * @param splitsOfTheHolding its splits, the earliest first ({@link StoredStockSplits#splitsOfTheHolding})
 */
public record TradesAfterTheSplits(List<TradeFact> tradesAsTyped, List<StockSplit> splitsOfTheHolding) {

    /** Every trade, in the order given; each one held through a split is translated through it. */
    public List<TradeFact> allTrades() {
        List<TradeFact> trades = tradesAsTyped;
        for (StockSplit split : splitsOfTheHolding) {
            trades = translateThroughTheSplit(split, trades);
        }
        return trades;
    }

    /**
     * Only the trades a split translated, as translated: what the screen shows beside a trade ("= 40 at $100"). A
     * translated trade has another quantity than the one typed, so it no longer equals it.
     */
    public List<TradeFact> translatedTrades() {
        List<TradeFact> allTrades = allTrades();
        List<TradeFact> translatedTrades = new ArrayList<>();
        for (int tradeIndex = 0; tradeIndex < tradesAsTyped.size(); tradeIndex++) {
            TradeFact tradeAfterTheSplits = allTrades.get(tradeIndex);
            if (!tradeAfterTheSplits.equals(tradesAsTyped.get(tradeIndex))) {
                translatedTrades.add(tradeAfterTheSplits);
            }
        }
        return translatedTrades;
    }

    /** Every investor's open position at the split translated through it; every other trade as it was. */
    private List<TradeFact> translateThroughTheSplit(StockSplit split, List<TradeFact> trades) {
        Set<TradeFact> openPositionTradesAtTheSplit = new HashSet<>();
        for (List<TradeFact> investorsTradesBeforeTheSplit : eachInvestorsTradesBeforeTheSplit(split, trades)) {
            openPositionTradesAtTheSplit.addAll(HoldingHistory.of(investorsTradesBeforeTheSplit).openPositionTrades());
        }
        return trades.stream()
                .map(trade -> openPositionTradesAtTheSplit.contains(trade) ? translated(trade, split) : trade)
                .toList();
    }

    /** The trades of the split's day or before, of each investor apart. */
    private List<List<TradeFact>> eachInvestorsTradesBeforeTheSplit(StockSplit split, List<TradeFact> trades) {
        Map<Long, List<TradeFact>> tradesBeforeTheSplitByInvestorId = new TreeMap<>();
        for (TradeFact trade : trades) {
            if (split.isBeforeTheSplit(trade.date())) {
                tradesBeforeTheSplitByInvestorId
                        .computeIfAbsent(trade.investorId(), investorId -> new ArrayList<>())
                        .add(trade);
            }
        }
        return List.copyOf(tradesBeforeTheSplitByInvestorId.values());
    }

    /** The same trade in the shares of after the split: more shares at a lower price, or fewer at a higher one. */
    private TradeFact translated(TradeFact trade, StockSplit split) {
        return new TradeFact(trade.id(), trade.investorId(), trade.date(), trade.side(),
                split.quantityAfterTheSplit(trade.quantity()), split.priceAfterTheSplit(trade.price()),
                trade.commission());
    }
}
