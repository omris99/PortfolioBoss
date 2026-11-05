package portfolioboss.calculation;

import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.Stock;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A stock's risk signs and its timing note (RECOMMENDATIONS_TODO.md, decision 5), from what Finnhub answered and from
 * its closes — the two things a scanned stock and a holding both have, so that each rule is written once
 * (MY_HOLDINGS_TODO.md, session 2). A figure Finnhub didn't give raises no sign: a loss-making company has no P/E, and
 * is only marked as losing money.
 *
 * @param stock    what Finnhub answered; {@code null} without an answer — no key, or the call failed
 * @param momentum the stock's figures on its last close; {@code null} without a single close
 */
public record RiskSignsCalculator(Stock stock, Momentum momentum) {

    /** "A debt more than twice the equity" — an opening number (decision 5), like the next two. */
    private static final double MOST_DEBT_TO_EQUITY = 2;
    private static final double MOST_PE_RATIO = 50;
    private static final int MOST_DAYS_UNTIL_EARNINGS_FOR_THE_NOTE = 14;
    private static final CompanyFacts NO_COMPANY_FACTS = new CompanyFacts(null, null, null, null);

    /** The signs the stock has, in the order of {@link RiskSign}; empty when it has none. */
    public List<RiskSign> riskSigns() {
        CompanyFacts companyFacts = companyFacts();
        List<RiskSign> riskSigns = new ArrayList<>();
        if (isBelow(companyFacts.netMarginPercent(), 0)) {
            riskSigns.add(RiskSign.LOSES_MONEY);
        }
        if (isAbove(companyFacts.debtToEquity(), MOST_DEBT_TO_EQUITY)) {
            riskSigns.add(RiskSign.HIGH_DEBT);
        }
        if (isAbove(companyFacts.peRatio(), MOST_PE_RATIO)) {
            riskSigns.add(RiskSign.EXPENSIVE);
        }
        if (isBelow(companyFacts.revenueGrowthPercent(), 0)) {
            riskSigns.add(RiskSign.REVENUE_FALLING);
        }
        if (momentum != null && Boolean.TRUE.equals(momentum.lowerThanAYearAgo())) {
            riskSigns.add(RiskSign.RECOVERING);
        }
        return List.copyOf(riskSigns);
    }

    /**
     * At least one of Finnhub's four figures. Without any, four of the five signs were never checked, so "no signs"
     * would only mean "not known" — such a scanned stock is not picked (the user's rule, 2026-10-27).
     */
    public boolean hasCompanyFacts() {
        CompanyFacts companyFacts = companyFacts();
        return Stream.of(companyFacts.revenueGrowthPercent(), companyFacts.netMarginPercent(),
                        companyFacts.debtToEquity(), companyFacts.peRatio())
                .anyMatch(Objects::nonNull);
    }

    /**
     * The timing note — "wait for the report of …": the next report is 0 to 14 days after {@code today}, both
     * inclusive. Not a sign: it says when to look, not how good the stock is. {@code false} with no report known.
     */
    public boolean reportsWithin14Days(LocalDate today) {
        if (stock == null || stock.nextEarningsDate() == null) {
            return false;
        }
        long daysUntilReport = ChronoUnit.DAYS.between(today, stock.nextEarningsDate().date());
        return daysUntilReport >= 0 && daysUntilReport <= MOST_DAYS_UNTIL_EARNINGS_FOR_THE_NOTE;
    }

    /** Finnhub's figures, or none of them without an answer — so that every check reads the same way. */
    private CompanyFacts companyFacts() {
        return stock == null || stock.companyFacts() == null ? NO_COMPANY_FACTS : stock.companyFacts();
    }

    private boolean isBelow(Double figure, double limit) {
        return figure != null && figure < limit;
    }

    private boolean isAbove(Double figure, double limit) {
        return figure != null && figure > limit;
    }
}
