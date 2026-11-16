package portfolioboss.tax;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The settlement day of a trade typed in by hand (CAPITAL_GAINS_TAX_TODO.md, session 3.1): a typed trade has a day but no
 * settlement day, while its twin in IB's Flex reports — the same stock on the same day — has the real one. A holding's
 * twin is found by IB's contract id, a manual position's, which has none, by its symbol; a trade without a twin (a date
 * typed wrong, say) gets the estimate of {@link UsSettlementDayEstimate}.
 *
 * <p>From the account's trades after the splits ({@link SplitAdjustedFlexTrades}): a buy made before a reverse split that
 * moved the stock to a new contract is then under the new contract, as the holding is.
 *
 * @param accountStocks the account's trades, stock by stock
 */
record TypedTradeSettlementDays(List<TradesOfOneStock> accountStocks) {

    protected LocalDate settlementDayOfAHoldingsTrade(int conId, LocalDate tradeDate) {
        List<TaxCalculationTrade> tradesOfTheStock = accountStocks.stream()
                .filter(stock -> Objects.equals(stock.conId(), conId))
                .flatMap(stock -> stock.tradesInOrder().stream())
                .toList();
        return settlementDayOfTheTwinAmong(tradesOfTheStock, tradeDate);
    }

    protected LocalDate settlementDayOfAManualPositionsTrade(String symbol, LocalDate tradeDate) {
        List<TaxCalculationTrade> tradesOfTheSymbol = accountStocks.stream()
                .flatMap(stock -> stock.tradesInOrder().stream())
                .filter(trade -> trade.symbol().equals(symbol))
                .toList();
        return settlementDayOfTheTwinAmong(tradesOfTheSymbol, tradeDate);
    }

    /**
     * Every execution of a stock on one day settles on the same day; should two settle apart, the earlier is taken.
     * Without a twin, the estimate.
     */
    private LocalDate settlementDayOfTheTwinAmong(List<TaxCalculationTrade> tradesOfTheStock, LocalDate tradeDate) {
        return tradesOfTheStock.stream()
                .filter(trade -> trade.tradeDate().equals(tradeDate))
                .map(TaxCalculationTrade::settlementDay)
                .min(Comparator.naturalOrder())
                .orElseGet(() -> new UsSettlementDayEstimate(tradeDate).estimatedSettlementDay());
    }
}
