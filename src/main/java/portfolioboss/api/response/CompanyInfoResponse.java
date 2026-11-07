package portfolioboss.api.response;

import portfolioboss.calculation.RiskSignsCalculator;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What Finnhub answered about a stock's company, with the risk signs and the timing note worked out from it on this
 * read (MY_HOLDINGS_TODO.md, session 3) — the same object for a holding and for a scanned stock, so that the UI shows
 * both with the same pieces. Finnhub's records go into the JSON as they are. The component names are JSON keys: one is
 * renamed or removed only together with {@code ui/src/types} and every reader of it, in the same change.
 *
 * @param finnhubAnsweredAt   when Finnhub answered: a holding's on the last run it answered for it, a scanned stock's
 *                            with its analysis
 * @param nextEarningsDate    {@code null} when Finnhub's calendar has no report in the next four months
 * @param reportsWithin14Days the timing note: the next report is due 0 to 14 days after the day it is counted from
 * @param companyFacts        Finnhub's four figures; each {@code null} when Finnhub doesn't give it
 * @param riskSigns           the risk signs (RECOMMENDATIONS_TODO.md, decision 5); {@code null} when Finnhub gave none
 *                            of the four figures — then they were never checked, which an empty list would hide
 */
public record CompanyInfoResponse(
        Instant finnhubAnsweredAt,
        EarningsDate nextEarningsDate,
        boolean reportsWithin14Days,
        CompanyFacts companyFacts,
        List<RiskSign> riskSigns) {

    /**
     * Copies Finnhub's answer and what the calculator works out from it and from the closes.
     *
     * @param riskSignsCalculator a calculator that has Finnhub's answer — never one without
     * @param today               the day the timing note counts from: the last sync's for a holding, the scan's for a
     *                            scanned stock
     */
    public CompanyInfoResponse(RiskSignsCalculator riskSignsCalculator, Instant finnhubAnsweredAt, LocalDate today) {
        this(finnhubAnsweredAt,
                riskSignsCalculator.stock().nextEarningsDate(),
                riskSignsCalculator.reportsWithin14Days(today),
                riskSignsCalculator.stock().companyFacts(),
                riskSignsCalculator.hasCompanyFacts() ? riskSignsCalculator.riskSigns() : null);
    }
}
