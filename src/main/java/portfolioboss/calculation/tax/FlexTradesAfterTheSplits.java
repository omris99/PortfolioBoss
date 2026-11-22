package portfolioboss.calculation.tax;

import portfolioboss.calculation.splits.StockSplit;
import portfolioboss.calculation.splits.StoredStockSplits;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.flex.FlexReportTrade;
import portfolioboss.model.portfolio.TradeSide;
import portfolioboss.model.tax.SaleNotComputedReason;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The account's stock trades in IB's Flex reports, stock by stock and in the order they were made, as they count after
 * the stored splits (CAPITAL_GAINS_TAX_TODO.md, decision 15; STOCK_SPLITS_TODO.md, decision 7). The Trades section shows
 * no split, and a reverse split can move a stock to a new contract: its buys are then under the old contract and its
 * later sales under the new one. So the trades of the account's open position at each split — since the account last
 * held none of the stock — are translated: the quantity times the split, the price divided by it, under the contract of
 * after it; the same cost, the same commission, the same settlement day and so the same rate. FIFO then knows nothing of
 * splits. A position sold in full before the split stays as IB wrote it, as on the broker's statement of its day.
 *
 * <p>Example: 500 shares bought at $8 under the old contract, then a reverse split of 1 for 4 into a new contract: the
 * buy counts as 125 shares at $32 of the new contract.
 *
 * <p>A corporate action not stored as a split — a spin-off, a merger, a split whose words and shares didn't agree
 * ({@link SplitsInFlexCorporateActions}) — leaves its contracts' trades as IB wrote them, and none of their sales is
 * computed ({@link SaleNotComputedReason#CORPORATE_ACTION_NOT_SUPPORTED}): never guessed.
 *
 * <p>In memory only, on every calculation: the stored rows stay as IB wrote them.
 *
 * @param flexTrades       every stored Flex trade; the currency conversions among them are left out
 * @param stockSplits      the stored splits
 * @param corporateActions every stored row of a corporate action: one not stored as a split leaves the sales of its
 *                         contracts not computed
 */
public record FlexTradesAfterTheSplits(List<FlexReportTrade> flexTrades, StoredStockSplits stockSplits,
                                       List<FlexReportCorporateAction> corporateActions) {

    private static final String STOCK = "STK";

    /** Below this the account holds none of a stock: a split of 1 for 3 leaves a fraction 16 digits can't hold exactly. */
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.000001");

    /**
     * IB's trade ids are numbers written as text — the order of executions with the same time —: a longer one is the
     * larger number, and among ids of the same length the text's order is the numbers'.
     */
    private static final Comparator<String> IB_TRADE_ID_ORDER =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    /** Every stock with a trade, by IB's contract id, each with its trades in the order they were made. */
    public List<TradesOfOneStock> tradesOfEveryStock() {
        return tradesByStock(stockTradesAfterTheSplits(), contractsOfActionsNotStoredAsSplits());
    }

    /**
     * The shares of the contract the account held just before the moment: what its trades made before it add up to,
     * after the splits given — which must all be before the moment ({@link SplitsInFlexCorporateActions} gives only
     * those).
     */
    public BigDecimal sharesHeldJustBefore(int conId, LocalDateTime moment) {
        return stockTradesAfterTheSplits().stream()
                .filter(trade -> trade.conId() == conId && trade.tradeAsIbWroteIt().tradeTime().isBefore(moment))
                .map(StockTrade::signedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<StockTrade> stockTradesAfterTheSplits() {
        List<StockTrade> trades = stockTradesAsIbWroteThem();
        for (StockSplit split : stockSplits.splits()) {
            trades = translateThroughTheSplit(split, trades);
        }
        return trades;
    }

    /** By IB's time, and IB's id for executions of the same second. */
    private List<StockTrade> stockTradesAsIbWroteThem() {
        return flexTrades.stream()
                .filter(trade -> STOCK.equals(trade.assetCategory()))
                .sorted(Comparator.comparing(FlexReportTrade::tradeTime)
                        .thenComparing(FlexReportTrade::ibTradeId, IB_TRADE_ID_ORDER))
                .map(StockTrade::new)
                .toList();
    }

    /** The account's open position at the split translated through it; every other trade as it was. */
    private List<StockTrade> translateThroughTheSplit(StockSplit split, List<StockTrade> tradesInOrder) {
        List<StockTrade> tradesBeforeTheSplit = tradesInOrder.stream()
                .filter(trade -> trade.conId() == split.conIdBeforeTheSplit()
                        && split.isBeforeTheSplit(trade.tradeAsIbWroteIt().tradeTime()))
                .toList();
        Set<StockTrade> openPositionTradesAtTheSplit = new HashSet<>(openPositionTrades(tradesBeforeTheSplit));
        return tradesInOrder.stream()
                .map(trade -> openPositionTradesAtTheSplit.contains(trade) ? trade.translatedThrough(split) : trade)
                .toList();
    }

    /**
     * The trades since the account last held none of the stock: a split counts only the shares held at it. Every trade
     * when it never held none; none when it held none at the end.
     */
    private List<StockTrade> openPositionTrades(List<StockTrade> tradesInOrder) {
        int firstTradeOfTheOpenPosition = 0;
        BigDecimal sharesHeld = BigDecimal.ZERO;
        for (int tradeIndex = 0; tradeIndex < tradesInOrder.size(); tradeIndex++) {
            sharesHeld = sharesHeld.add(tradesInOrder.get(tradeIndex).signedQuantity());
            if (sharesHeld.abs().compareTo(QUANTITY_TOLERANCE) <= 0) {
                firstTradeOfTheOpenPosition = tradeIndex + 1;
                sharesHeld = BigDecimal.ZERO;
            }
        }
        return tradesInOrder.subList(firstTradeOfTheOpenPosition, tradesInOrder.size());
    }

    private Set<Integer> contractsOfActionsNotStoredAsSplits() {
        Set<Integer> contracts = new HashSet<>();
        for (FlexReportCorporateAction row : corporateActions) {
            if (!stockSplits.isStored(row.ibActionId())) {
                contracts.add(row.conId());
            }
        }
        return contracts;
    }

    /** Each contract's trades in the order they were made, with the reason that holds for every sale of it, if any. */
    private List<TradesOfOneStock> tradesByStock(List<StockTrade> tradesInOrder,
                                                 Set<Integer> contractsOfActionsNotStoredAsSplits) {
        Map<Integer, List<StockTrade>> tradesByConId = new TreeMap<>();
        for (StockTrade trade : tradesInOrder) {
            tradesByConId.computeIfAbsent(trade.conId(), conId -> new ArrayList<>()).add(trade);
        }
        List<TradesOfOneStock> tradesOfEveryStock = new ArrayList<>();
        tradesByConId.forEach((conId, tradesOfTheStock) -> tradesOfEveryStock.add(new TradesOfOneStock(conId,
                tradesOfTheStock.stream().map(StockTrade::toTaxCalculationTrade).toList(),
                notComputedReasonOfEverySale(conId, tradesOfTheStock, contractsOfActionsNotStoredAsSplits))));
        return tradesOfEveryStock;
    }

    private SaleNotComputedReason notComputedReasonOfEverySale(int conId, List<StockTrade> tradesOfTheStock,
                                                               Set<Integer> contractsOfActionsNotStoredAsSplits) {
        if (contractsOfActionsNotStoredAsSplits.contains(conId)) {
            return SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED;
        }
        boolean aCommissionInAnotherCurrency = tradesOfTheStock.stream().anyMatch(trade ->
                !trade.tradeAsIbWroteIt().ibCommissionCurrency().equals(trade.tradeAsIbWroteIt().currency()));
        return aCommissionInAnotherCurrency ? SaleNotComputedReason.COMMISSION_IN_ANOTHER_CURRENCY : null;
    }

    /**
     * A stock execution, as the splits so far left it.
     *
     * @param tradeAsIbWroteIt the execution as IB wrote it
     * @param conId            its contract now: IB's, or the new one of a reverse split that moved the stock
     * @param signedQuantity   its shares now, with IB's sign — negative for a sale
     * @param price            the price of one of those shares
     */
    private record StockTrade(FlexReportTrade tradeAsIbWroteIt, int conId, BigDecimal signedQuantity,
                              BigDecimal price) {

        private StockTrade(FlexReportTrade tradeAsIbWroteIt) {
            this(tradeAsIbWroteIt, tradeAsIbWroteIt.conId(), tradeAsIbWroteIt.quantity(),
                    tradeAsIbWroteIt.tradePrice());
        }

        /** The same trade in the shares of after the split, under the contract of after it. */
        private StockTrade translatedThrough(StockSplit split) {
            return new StockTrade(tradeAsIbWroteIt, split.conIdAfterTheSplit(),
                    split.quantityAfterTheSplit(signedQuantity), split.priceAfterTheSplit(price));
        }

        /** IB's signs turned into the calculation's: a side, and a positive quantity and commission. */
        private TaxCalculationTrade toTaxCalculationTrade() {
            TradeSide side = signedQuantity.signum() < 0 ? TradeSide.SELL : TradeSide.BUY;
            return new TaxCalculationTrade(tradeAsIbWroteIt.ibTradeId(), tradeAsIbWroteIt.symbol(),
                    tradeAsIbWroteIt.currency(), tradeAsIbWroteIt.tradeDate(), tradeAsIbWroteIt.settleDate(), side,
                    signedQuantity.abs(), price, tradeAsIbWroteIt.ibCommission().negate());
        }
    }
}
