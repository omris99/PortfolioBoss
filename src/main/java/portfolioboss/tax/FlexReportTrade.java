package portfolioboss.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One execution in a Flex report's Trades section, as IB wrote it — a buy or a sell of a stock, or a currency conversion.
 * IB's signs are kept: a sale's {@code quantity} is negative, the commission is negative, and {@code netCash} is what
 * the account received (positive) or paid (negative), the commission included. Which of it is taxed, and how, is the
 * calculation's question (CAPITAL_GAINS_TAX_TODO.md, session 3), not this record's.
 *
 * @param ibTradeId            IB's {@code tradeID}, unique to the execution
 * @param ibOrderId            IB's {@code ibOrderID}: the executions of one order share it; {@code null} when IB gives
 *                             none
 * @param conId                IB's contract id ({@code conid}), the key the rest of the app knows a stock by
 * @param symbol               IB's symbol
 * @param assetCategory        {@code STK} for a stock, {@code CASH} for a currency conversion
 * @param currency             the trade's currency
 * @param tradeDate            the day of the trade
 * @param tradeTime            when it was executed, in New York's time, as IB writes it
 * @param settleDate           IB's {@code settleDateTarget}: the day the money and the shares actually move — the day
 *                             whose exchange rate the tax uses (decision 1)
 * @param buySell              IB's {@code BUY} or {@code SELL}
 * @param quantity             the shares (or the currency) bought, negative when sold
 * @param tradePrice           the price of one share
 * @param ibCommission         IB's commission, negative
 * @param ibCommissionCurrency the commission's currency, which is not always the trade's
 * @param netCash              what the trade brought into the account, or took out of it, after the commission
 * @param transactionType      IB's kind of trade — {@code ExchTrade} for one made on an exchange —; {@code null} when IB
 *                             gives none
 */
public record FlexReportTrade(
        String ibTradeId,
        String ibOrderId,
        int conId,
        String symbol,
        String assetCategory,
        String currency,
        LocalDate tradeDate,
        LocalDateTime tradeTime,
        LocalDate settleDate,
        String buySell,
        BigDecimal quantity,
        BigDecimal tradePrice,
        BigDecimal ibCommission,
        String ibCommissionCurrency,
        BigDecimal netCash,
        String transactionType) {
}
