package portfolioboss.api.marketdata.response;

import portfolioboss.calculation.marketdata.RiskSignsCalculator;
import portfolioboss.model.marketdata.RiskSign;
import portfolioboss.sources.CompanyFacts;
import portfolioboss.sources.EarningsDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What a source of company figures answered about a stock's company — Finviz or Finnhub, the first that answered
 * (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9) —, with the risk signs and the timing note worked out from it on this read
 * (MY_HOLDINGS_TODO.md, session 3) — the same object for a holding and for a scanned stock, so that the UI shows both
 * with the same pieces. The source's records go into the JSON as they are. The component names are JSON keys: one is
 * renamed or removed only together with {@code ui/src/types} and every reader of it, in the same change.
 *
 * @param answeredBy          the source that answered, as the screens name it: "Finviz", "Finnhub"
 * @param answeredAt          when it answered: a holding's on the last run a source answered for it, a scanned stock's
 *                            with its analysis
 * @param nextEarningsDate    {@code null} when the source knows no report in the next four months
 * @param reportsWithin14Days the timing note: the next report is due 0 to 14 days after the day it is counted from
 * @param companyFacts        the source's four figures; each {@code null} when the source doesn't give it
 * @param riskSigns           the risk signs (RECOMMENDATIONS_TODO.md, decision 5); {@code null} when the source gave
 *                            none of the four figures — then they were never checked, which an empty list would hide
 */
public record CompanyInfoResponse(
        String answeredBy,
        Instant answeredAt,
        EarningsDate nextEarningsDate,
        boolean reportsWithin14Days,
        CompanyFacts companyFacts,
        List<RiskSign> riskSigns) {

    /**
     * Copies the source's answer and what the calculator works out from it and from the closes.
     *
     * @param riskSignsCalculator a calculator that has the source's answer — never one without
     * @param answeredAt          when the source answered
     * @param today               the day the timing note counts from: the last sync's for a holding, the scan's for a
     *                            scanned stock
     */
    public CompanyInfoResponse(RiskSignsCalculator riskSignsCalculator, Instant answeredAt, LocalDate today) {
        this(riskSignsCalculator.stock().answeredBy(),
                answeredAt,
                riskSignsCalculator.stock().nextEarningsDate(),
                riskSignsCalculator.reportsWithin14Days(today),
                riskSignsCalculator.stock().companyFacts(),
                riskSignsCalculator.hasCompanyFacts() ? riskSignsCalculator.riskSigns() : null);
    }
}
