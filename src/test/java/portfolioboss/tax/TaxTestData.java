package portfolioboss.tax;

import portfolioboss.exchangerates.ExchangeRates;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Made-up trades and exchange rates for the tax calculation's tests, so that each test reads in a line: a stock in
 * dollars, ABCD, without commissions unless a test gives one, traded the business day before it settles.
 */
final class TaxTestData {

    protected static final String US_DOLLAR = "USD";

    private TaxTestData() {
    }

    protected static TaxCalculationTrade buy(String tradeId, String quantity, String price, LocalDate settlementDay) {
        return tradeOf(tradeId, TradeSide.BUY, quantity, price, "0", settlementDay);
    }

    protected static TaxCalculationTrade sale(String tradeId, String quantity, String price, LocalDate settlementDay) {
        return tradeOf(tradeId, TradeSide.SELL, quantity, price, "0", settlementDay);
    }

    protected static TaxCalculationTrade tradeOf(String tradeId, TradeSide side, String quantity, String price,
                                                 String commission, LocalDate settlementDay) {
        return new TaxCalculationTrade(tradeId, "ABCD", US_DOLLAR, settlementDay.minusDays(1), settlementDay, side,
                new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(commission));
    }

    /** One stock's trades, in the order given, with no reason holding for every sale. */
    protected static TradesOfOneStock stockOf(TaxCalculationTrade... tradesInOrder) {
        return new TradesOfOneStock(1111, List.of(tradesInOrder), null);
    }

    /** The dollar's rates, a day and its rate after another: {@code dollarRates("2026-03-02", "3.6", …)}. */
    protected static Map<String, ExchangeRates> dollarRates(String... daysAndRates) {
        TreeMap<LocalDate, BigDecimal> ratesByDate = new TreeMap<>();
        for (int index = 0; index < daysAndRates.length; index += 2) {
            ratesByDate.put(LocalDate.parse(daysAndRates[index]), new BigDecimal(daysAndRates[index + 1]));
        }
        return Map.of(US_DOLLAR, new ExchangeRates(US_DOLLAR, ratesByDate));
    }

    /**
     * A rate of 1 for every day from the first of January of {@code firstYear} to the end of {@code lastYear}: a gain in
     * dollars is then the same in shekels, both ways.
     */
    protected static Map<String, ExchangeRates> dollarRatesOfOneForTheYears(int firstYear, int lastYear) {
        TreeMap<LocalDate, BigDecimal> ratesByDate = new TreeMap<>();
        for (LocalDate day = LocalDate.of(firstYear, 1, 1); day.getYear() <= lastYear; day = day.plusDays(1)) {
            ratesByDate.put(day, BigDecimal.ONE);
        }
        return Map.of(US_DOLLAR, new ExchangeRates(US_DOLLAR, ratesByDate));
    }

    /**
     * A sale whose taxable gain is {@code taxableGain} shekels, settled on {@code settlementDay}: one share bought and
     * sold the same day, at a rate of 1. A negative gain is a loss.
     */
    protected static SaleTaxableGain saleWithTaxableGainOf(String taxableGain, LocalDate settlementDay) {
        BigDecimal buyPrice = new BigDecimal("100000");
        String salePrice = buyPrice.add(new BigDecimal(taxableGain)).toPlainString();
        FifoSale fifoSale = new FifoSalesCalculator(List.of(
                buy("buy on " + settlementDay, "1", buyPrice.toPlainString(), settlementDay),
                sale("sale on " + settlementDay, "1", salePrice, settlementDay))).sales().get(0);
        return new SaleTaxableGain(fifoSale, null,
                dollarRatesOfOneForTheYears(settlementDay.getYear(), settlementDay.getYear()));
    }

    /** The settlement day's entry of a single sale with that taxable gain ({@link #saleWithTaxableGainOf}). */
    protected static SettlementDayEntry entryWithTaxableGainOf(String taxableGain, LocalDate settlementDay) {
        return new SettlementDayEntry(settlementDay, List.of(saleWithTaxableGainOf(taxableGain, settlementDay)),
                List.of(), Map.of());
    }
}
