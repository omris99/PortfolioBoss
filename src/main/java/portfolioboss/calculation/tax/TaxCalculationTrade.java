package portfolioboss.calculation.tax;

import portfolioboss.flex.FlexReportTrade;
import portfolioboss.model.portfolio.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One buy or sale in the shape the capital gains tax calculation works on (CAPITAL_GAINS_TAX_TODO.md, session 3.1) — the
 * same for its two sources: an execution in IB's Flex reports, for the account's tax, and a trade typed in by hand, for an
 * investor's. Unlike IB's own record ({@link FlexReportTrade}), the quantity and the commission are always positive and
 * the side says which way the trade went; unlike a typed trade, it has its settlement day.
 *
 * @param tradeId       where it came from, for the messages and the check against IB: IB's {@code tradeID} for a Flex
 *                      execution, the {@code trade} row's id for a typed trade
 * @param symbol        the stock's symbol
 * @param currency      the trade's currency, in which the price and the commission are
 * @param tradeDate     the day of the trade
 * @param settlementDay the day the money and the shares moved — the day whose exchange rate the tax takes (decision 1)
 * @param side          a buy or a sale
 * @param quantity      the shares, always positive
 * @param price         the price of one share
 * @param commission    what the broker charged for it, always positive
 */
public record TaxCalculationTrade(String tradeId, String symbol, String currency, LocalDate tradeDate, LocalDate settlementDay,
                           TradeSide side, BigDecimal quantity, BigDecimal price, BigDecimal commission) {

    protected boolean isSale() {
        return side == TradeSide.SELL;
    }

    /** {@code quantity × price}, the commission left out. */
    protected BigDecimal amount() {
        return quantity.multiply(price);
    }
}
