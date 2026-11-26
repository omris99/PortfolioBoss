package portfolioboss.ib;

import java.util.List;
import java.util.Map;

/**
 * What one {@code ./run.sh} connection read: the portfolio, the daily closes of its holdings, of SPY, of the stocks the
 * scan kept (maybe none) and of the stocks asked for by contract or by symbol, the scan — {@code null} when none was due
 * or none answered —, the holdings' contract details and analysts' price targets (MY_HOLDINGS_TODO.md, session 2), and
 * the contracts of the stocks asked for by symbol (RECOMMENDATIONS_FINVIZ_TODO.md, session 2).
 *
 * @param holdingContractDetailsByConId     by contract id, the contract details of each holding they came for: its
 *                                          sector (decision 11) and the name its headlines are read with
 * @param holdingAnalystPriceTargetsByConId by contract id, the price targets in each holding's Dow Jones headlines — an
 *                                          empty list when its headlines hold none; a holding whose headlines did not
 *                                          come is missing, and keeps the targets stored before. A holding the scan
 *                                          found too has the same targets in the scan's map: the headlines were read
 *                                          once
 * @param conIdsFoundBySymbol               by symbol, the contract found on a US stock exchange for each stock asked
 *                                          for by its symbol; a symbol not found is missing. Empty when none was asked
 */
public record TwsReading(PortfolioSnapshot snapshot, Map<Integer, List<DailyClose>> dailyCloses, Scan scan,
                         Map<Integer, StockContractDetails> holdingContractDetailsByConId,
                         Map<Integer, List<AnalystPriceTarget>> holdingAnalystPriceTargetsByConId,
                         Map<String, Integer> conIdsFoundBySymbol) {

    /** Without the contracts of stocks asked for by symbol: none was asked. */
    public TwsReading(PortfolioSnapshot snapshot, Map<Integer, List<DailyClose>> dailyCloses, Scan scan,
                      Map<Integer, StockContractDetails> holdingContractDetailsByConId,
                      Map<Integer, List<AnalystPriceTarget>> holdingAnalystPriceTargetsByConId) {
        this(snapshot, dailyCloses, scan, holdingContractDetailsByConId, holdingAnalystPriceTargetsByConId, Map.of());
    }

    /** Without the holdings' contract details and targets: what is stored before stays as it is. */
    public TwsReading(PortfolioSnapshot snapshot, Map<Integer, List<DailyClose>> dailyCloses, Scan scan) {
        this(snapshot, dailyCloses, scan, Map.of(), Map.of());
    }
}
