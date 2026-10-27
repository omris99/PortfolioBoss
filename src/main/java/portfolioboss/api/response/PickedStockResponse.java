package portfolioboss.api.response;

import portfolioboss.db.ScanCandidateEntity;

/**
 * One stock of the latest recommendation. The component names are JSON keys.
 *
 * @param candidateId its row in the scan the recommendation came from
 * @param conId       IB's contract id: when the recommendation is of an earlier scan, the stock is found in today's lists
 *                    by it (decision 12), its row there being a new one
 */
public record PickedStockResponse(long candidateId, int conId, String symbol) {

    public PickedStockResponse(ScanCandidateEntity candidate) {
        this(candidate.id(), candidate.conId(), candidate.symbol());
    }
}
