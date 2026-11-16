package portfolioboss.tax;

import portfolioboss.exchangerates.ExchangeRates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The taxable gain of one sale, in shekels (CAPITAL_GAINS_TAX_TODO.md, session 3.3): the sum of the taxable gains of the
 * shares it took from each buy ({@link TaxableGainOfSharesFromOneBuy}), each buy at its own settlement day's rate and the
 * sale at its own. When it can't be computed it says why ({@link SaleNotComputedReason}) and has no gain — the sale is
 * left out of the year's balance, never guessed.
 *
 * @param fifoSale                               the sale, with the buys FIFO took its shares from
 * @param notComputedReasonOfEverySaleOfTheStock a reason that holds for every sale of its stock
 *                                               ({@link TradesOfOneStock}); {@code null} when there is none
 * @param exchangeRatesByCurrency                the stored rates of each currency the Bank of Israel publishes —
 *                                               a currency missing here has none
 */
record SaleTaxableGain(FifoSale fifoSale, SaleNotComputedReason notComputedReasonOfEverySaleOfTheStock,
                       Map<String, ExchangeRates> exchangeRatesByCurrency) {

    protected TaxCalculationTrade sale() {
        return fifoSale.sale();
    }

    /** {@code null} when the sale is computed; otherwise the first reason that applies, in {@link SaleNotComputedReason}'s order. */
    protected SaleNotComputedReason notComputedReason() {
        if (notComputedReasonOfEverySaleOfTheStock != null) {
            return notComputedReasonOfEverySaleOfTheStock;
        }
        if (fifoSale.soldMoreThanBought()) {
            return SaleNotComputedReason.SOLD_MORE_THAN_BOUGHT;
        }
        ExchangeRates exchangeRates = exchangeRatesOfTheTradesCurrency();
        if (exchangeRates == null) {
            return SaleNotComputedReason.NO_RATE_FOR_THE_CURRENCY;
        }
        if (!everySettlementDayHasItsRate(exchangeRates)) {
            return SaleNotComputedReason.RATE_NOT_PUBLISHED_YET;
        }
        return null;
    }

    protected boolean isComputed() {
        return notComputedReason() == null;
    }

    /** The shares taken from each buy with their taxable gain, the earliest buy first; empty when not computed. */
    protected List<TaxableGainOfSharesFromOneBuy> taxableGainsOfEachBuy() {
        if (!isComputed()) {
            return List.of();
        }
        ExchangeRates exchangeRates = exchangeRatesOfTheTradesCurrency();
        BigDecimal saleRate = rateOf(exchangeRates, sale().settlementDay());
        return fifoSale.sharesSoldFromEachBuy().stream()
                .map(sharesSold -> new TaxableGainOfSharesFromOneBuy(sharesSold,
                        rateOf(exchangeRates, sharesSold.buy().settlementDay()), saleRate))
                .toList();
    }

    /** In shekels: negative for a loss; {@code null} when not computed. */
    protected BigDecimal taxableGain() {
        if (!isComputed()) {
            return null;
        }
        return taxableGainsOfEachBuy().stream()
                .map(TaxableGainOfSharesFromOneBuy::taxableGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ExchangeRates exchangeRatesOfTheTradesCurrency() {
        return exchangeRatesByCurrency.get(sale().currency());
    }

    /** The sale's settlement day and the settlement day of every buy it took shares from. */
    private boolean everySettlementDayHasItsRate(ExchangeRates exchangeRates) {
        boolean saleDayHasItsRate = exchangeRates.knowsRateForSettlementDay(sale().settlementDay());
        boolean everyBuyDayHasItsRate = fifoSale.sharesSoldFromEachBuy().stream()
                .allMatch(sharesSold -> exchangeRates.knowsRateForSettlementDay(sharesSold.buy().settlementDay()));
        return saleDayHasItsRate && everyBuyDayHasItsRate;
    }

    /** Only after {@link #everySettlementDayHasItsRate}: the rate is known. */
    private BigDecimal rateOf(ExchangeRates exchangeRates, LocalDate settlementDay) {
        return exchangeRates.rateForSettlementDay(settlementDay).orElseThrow();
    }
}
