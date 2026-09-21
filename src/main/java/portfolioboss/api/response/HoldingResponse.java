package portfolioboss.api.response;

import portfolioboss.model.Holding;

/**
 * One holding as the UI receives it. Jackson turns the record into JSON using the component names,
 * so they are the JSON keys and must stay stable ({@code ui/src/types/portfolio.ts} mirrors them).
 * A figure IB did not report is {@code null}.
 */
public record HoldingResponse(
        String symbol,
        String secType,
        String currency,
        Double position,
        Double averageCost,
        Double marketPrice,
        Double marketValue,
        Double unrealizedPnl,
        Double realizedPnl,
        String account,
        Double costBasis,
        Double unrealizedPnlPercent) {

    static HoldingResponse from(Holding holding) {
        return new HoldingResponse(
                holding.symbol(),
                holding.secType(),
                holding.currency(),
                JsonNumbers.finiteOrNull(holding.position()),
                JsonNumbers.finiteOrNull(holding.averageCost()),
                JsonNumbers.finiteOrNull(holding.marketPrice()),
                JsonNumbers.finiteOrNull(holding.marketValue()),
                JsonNumbers.finiteOrNull(holding.unrealizedPnl()),
                JsonNumbers.finiteOrNull(holding.realizedPnl()),
                holding.account(),
                JsonNumbers.finiteOrNull(holding.costBasis()),
                JsonNumbers.finiteOrNull(holding.unrealizedPnlPercent()));
    }
}
