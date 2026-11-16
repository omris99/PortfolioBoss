package portfolioboss.tax;

import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The account's stock trades in IB's Flex reports, stock by stock and in the order they were made, in the shares of
 * today (CAPITAL_GAINS_TAX_TODO.md, session 3.0, decision 15). The Trades section shows no split or reverse split, and a
 * reverse split can move a stock to a new contract: its buys are then under the old contract and its later sales under
 * the new one. So every trade of the old contract made before such an action is restated in the shares of after it —
 * the quantity times the ratio, the price divided by it —, under the new contract: the same cost, the same commission,
 * the same settlement day and so the same rate. That is how IB shows a history after a split, and how the trades were
 * typed in by hand; FIFO then knows nothing of splits.
 *
 * <p>Example: 500 shares bought at $8 under the old contract, then a reverse split of 1 for 4 — IB's two rows, −2,000
 * of the old contract and +500 of the new, a ratio of 500 ÷ 2,000 — makes the buy 125 shares at $32 of the new contract.
 *
 * <p>Only the shape seen is followed: two rows of one action, one taking shares away and one adding them, a reverse split
 * ({@code RS}) or a split ({@code FS}, assumed to be written the same way), without money, and the old contract's trades
 * adding up, just before it, to the very shares it takes away. Any other action — a spin-off, a merger, cash for a
 * fraction of a share, a trade missing — leaves its contracts' trades as IB wrote them, and none of their sales is
 * computed ({@link SaleNotComputedReason#CORPORATE_ACTION_NOT_SUPPORTED}): never guessed.
 *
 * <p>In memory only, on every calculation: the stored rows stay as IB wrote them.
 *
 * @param flexTrades       every stored Flex trade; the currency conversions among them are left out
 * @param corporateActions every stored row of a corporate action
 */
record SplitAdjustedFlexTrades(List<FlexReportTrade> flexTrades, List<FlexReportCorporateAction> corporateActions) {

    private static final String STOCK = "STK";

    /** IB's types of the actions followed: a reverse split (seen in the account) and a split (assumed alike). */
    private static final Set<String> SPLIT_TYPES = Set.of("RS", "FS");

    /** Below this two quantities count as the same: a split of 1 for 3 leaves a fraction 16 digits can't hold exactly. */
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.000001");

    /** 16 significant digits, as in {@code calculation.ClosedPosition}: a quantity or a price through a ratio. */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    /**
     * IB's trade ids are numbers written as text — the order of executions with the same time —: a longer one is the
     * larger number, and among ids of the same length the text's order is the numbers'.
     */
    private static final Comparator<String> IB_TRADE_ID_ORDER =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    /** Every stock with a trade, by IB's contract id, each with its trades in the order they were made. */
    protected List<TradesOfOneStock> tradesOfEveryStock() {
        List<TradeInTodaysShares> stockTrades = stockTradesAsIbWroteThem();
        Set<Integer> contractsOfActionsNotFollowed = new HashSet<>();
        for (CorporateAction corporateAction : corporateActionsInTimeOrder()) {
            if (canBeFollowed(corporateAction, stockTrades)) {
                stockTrades = restatedThrough(corporateAction, stockTrades);
            } else {
                contractsOfActionsNotFollowed.addAll(corporateAction.conIds());
            }
        }
        return tradesByStock(stockTrades, contractsOfActionsNotFollowed);
    }

    private List<TradeInTodaysShares> stockTradesAsIbWroteThem() {
        return flexTrades.stream()
                .filter(trade -> STOCK.equals(trade.assetCategory()))
                .map(TradeInTodaysShares::new)
                .toList();
    }

    /** The rows grouped into actions by IB's {@code actionID}, the earliest action first. */
    private List<CorporateAction> corporateActionsInTimeOrder() {
        Map<String, List<FlexReportCorporateAction>> rowsByActionId = new LinkedHashMap<>();
        for (FlexReportCorporateAction row : corporateActions) {
            rowsByActionId.computeIfAbsent(row.ibActionId(), actionId -> new ArrayList<>()).add(row);
        }
        return rowsByActionId.values().stream()
                .map(CorporateAction::new)
                .sorted(Comparator.comparing(CorporateAction::actionTime))
                .toList();
    }

    /**
     * A split of the shape seen, whose old contract's trades add up, just before it, to the shares it takes away —
     * otherwise a trade is missing, and the ratio would restate the wrong shares.
     */
    private boolean canBeFollowed(CorporateAction corporateAction, List<TradeInTodaysShares> stockTrades) {
        if (!corporateAction.isSplitOfTheShapeSeen()) {
            return false;
        }
        BigDecimal sharesHeldJustBefore = stockTrades.stream()
                .filter(corporateAction::restates)
                .map(TradeInTodaysShares::signedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = sharesHeldJustBefore.subtract(corporateAction.sharesTakenAway()).abs();
        return difference.compareTo(QUANTITY_TOLERANCE) <= 0;
    }

    private List<TradeInTodaysShares> restatedThrough(CorporateAction split, List<TradeInTodaysShares> stockTrades) {
        return stockTrades.stream()
                .map(trade -> split.restates(trade) ? trade.restatedThrough(split) : trade)
                .toList();
    }

    /**
     * Each contract's trades in the order they were made — by IB's time, and IB's id for executions of the same second —,
     * with the reason that holds for every sale of it, if any.
     */
    private List<TradesOfOneStock> tradesByStock(List<TradeInTodaysShares> stockTrades,
                                                 Set<Integer> contractsOfActionsNotFollowed) {
        Map<Integer, List<TradeInTodaysShares>> tradesByConId = new TreeMap<>();
        for (TradeInTodaysShares trade : stockTrades) {
            tradesByConId.computeIfAbsent(trade.conId(), conId -> new ArrayList<>()).add(trade);
        }
        Comparator<TradeInTodaysShares> orderTheyWereMade = Comparator
                .comparing((TradeInTodaysShares trade) -> trade.ibTrade().tradeTime())
                .thenComparing(trade -> trade.ibTrade().ibTradeId(), IB_TRADE_ID_ORDER);

        List<TradesOfOneStock> tradesOfEveryStock = new ArrayList<>();
        tradesByConId.forEach((conId, tradesOfTheStock) -> {
            List<TradeInTodaysShares> tradesInOrder = tradesOfTheStock.stream().sorted(orderTheyWereMade).toList();
            tradesOfEveryStock.add(new TradesOfOneStock(conId,
                    tradesInOrder.stream().map(TradeInTodaysShares::toTaxCalculationTrade).toList(),
                    notComputedReasonOfEverySale(conId, tradesInOrder, contractsOfActionsNotFollowed)));
        });
        return tradesOfEveryStock;
    }

    private SaleNotComputedReason notComputedReasonOfEverySale(int conId, List<TradeInTodaysShares> tradesInOrder,
                                                               Set<Integer> contractsOfActionsNotFollowed) {
        if (contractsOfActionsNotFollowed.contains(conId)) {
            return SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED;
        }
        boolean aCommissionInAnotherCurrency = tradesInOrder.stream()
                .anyMatch(trade -> !trade.ibTrade().ibCommissionCurrency().equals(trade.ibTrade().currency()));
        return aCommissionInAnotherCurrency ? SaleNotComputedReason.COMMISSION_IN_ANOTHER_CURRENCY : null;
    }

    /**
     * An execution as IB reported it, with its contract, quantity and price in the shares of after every split followed
     * so far.
     *
     * @param ibTrade        the execution as IB wrote it
     * @param conId          its contract today: IB's, or the new one of a reverse split that moved the stock
     * @param signedQuantity its shares today, with IB's sign — negative for a sale
     * @param price          its price of one share today
     */
    private record TradeInTodaysShares(FlexReportTrade ibTrade, int conId, BigDecimal signedQuantity,
                                       BigDecimal price) {

        private TradeInTodaysShares(FlexReportTrade ibTrade) {
            this(ibTrade, ibTrade.conId(), ibTrade.quantity(), ibTrade.tradePrice());
        }

        /** The same trade in the shares of after the split: more shares at a lower price, or fewer at a higher one. */
        private TradeInTodaysShares restatedThrough(CorporateAction split) {
            BigDecimal quantityAfter = signedQuantity.multiply(split.sharesAdded())
                    .divide(split.sharesTakenAway(), DIVISION_PRECISION);
            BigDecimal priceAfter = price.multiply(split.sharesTakenAway())
                    .divide(split.sharesAdded(), DIVISION_PRECISION);
            return new TradeInTodaysShares(ibTrade, split.newConId(), quantityAfter, priceAfter);
        }

        /** IB's signs turned into the calculation's: a side, and a positive quantity and commission. */
        private TaxCalculationTrade toTaxCalculationTrade() {
            TradeSide side = signedQuantity.signum() < 0 ? TradeSide.SELL : TradeSide.BUY;
            return new TaxCalculationTrade(ibTrade.ibTradeId(), ibTrade.symbol(), ibTrade.currency(),
                    ibTrade.tradeDate(), ibTrade.settleDate(), side, signedQuantity.abs(), price,
                    ibTrade.ibCommission().negate());
        }
    }

    /**
     * The rows of one corporate action, as IB grouped them by {@code actionID}.
     *
     * @param rows the action's rows, in IB's order
     */
    private record CorporateAction(List<FlexReportCorporateAction> rows) {

        private LocalDateTime actionTime() {
            return rows.get(0).actionTime();
        }

        private Set<Integer> conIds() {
            Set<Integer> conIds = new HashSet<>();
            rows.forEach(row -> conIds.add(row.conId()));
            return conIds;
        }

        /** Two stock rows of a split's type at the same time, one taking shares away and one adding them, without money. */
        private boolean isSplitOfTheShapeSeen() {
            if (rows.size() != 2) {
                return false;
            }
            boolean bothSplitRowsOfAStock = rows.stream().allMatch(row ->
                    SPLIT_TYPES.contains(row.actionType()) && STOCK.equals(row.assetCategory()));
            boolean withoutMoney = rows.stream().allMatch(row ->
                    row.proceeds().signum() == 0 && row.amount().signum() == 0);
            boolean atTheSameTime = rows.get(0).actionTime().equals(rows.get(1).actionTime());
            boolean oneTakesAwayAndOneAdds = rows.get(0).quantity().signum() * rows.get(1).quantity().signum() < 0;
            return bothSplitRowsOfAStock && withoutMoney && atTheSameTime && oneTakesAwayAndOneAdds;
        }

        /** Whether the trade is one the split restates: of the old contract, and made before it. */
        private boolean restates(TradeInTodaysShares trade) {
            return trade.conId() == rowTakingSharesAway().conId() && trade.ibTrade().tradeTime().isBefore(actionTime());
        }

        private int newConId() {
            return rowAddingShares().conId();
        }

        /** Positive: the old contract's shares the action took away. */
        private BigDecimal sharesTakenAway() {
            return rowTakingSharesAway().quantity().negate();
        }

        private BigDecimal sharesAdded() {
            return rowAddingShares().quantity();
        }

        private FlexReportCorporateAction rowTakingSharesAway() {
            return rows.get(0).quantity().signum() < 0 ? rows.get(0) : rows.get(1);
        }

        private FlexReportCorporateAction rowAddingShares() {
            return rows.get(0).quantity().signum() > 0 ? rows.get(0) : rows.get(1);
        }
    }
}
