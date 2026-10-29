package portfolioboss.api;

import portfolioboss.calculation.AnalystTargetCalculator;
import portfolioboss.calculation.HotStockCalculator;
import portfolioboss.db.ScanCandidateEntity;

/**
 * A scanned stock (a candidate) and the Recommendations page's list it is in — HOT, TOO_HOT or CORRECTING —, by the rule
 * of RECOMMENDATIONS_TODO.md, decision 1. The two are kept together so that the stocks can be put in the page's order,
 * and so that the analysis takes the short list from that same order. Only a stock that is in one of the three lists
 * has one. With them, the analysts' target (decision 14): like the list, it is known for every stock before anything is
 * paid for, and it decides which stocks the short list takes.
 *
 * @param candidate     the stock's row in the scan: its id, symbol, name, exchange and sector, as IB sent them
 * @param hotStock      what the rule says about its closes: its list, its place in it, and the momentum figures it was
 *                      worked out from
 * @param analystTarget the analysts' target from its Dow Jones headlines, against its last close
 */
public record CandidateWithItsList(ScanCandidateEntity candidate, HotStockCalculator hotStock,
                                   AnalystTargetCalculator analystTarget) {
}
