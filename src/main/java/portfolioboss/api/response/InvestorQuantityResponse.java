package portfolioboss.api.response;

import portfolioboss.calculation.InvestorPart;

import java.math.BigDecimal;

/**
 * An investor's part of a holding, as the UI receives it in {@code HoldingResponse.investorQuantities}: 39 NVDA is
 * {@code (1, 15, …)} and {@code (2, 24, …)}. See {@code PositionTrades.partsByInvestor}. {@code sharesValue},
 * {@code sharesCost}, {@code unrealizedPnl} and {@code unrealizedPnlPercent} were added when each investor's profit in a
 * holding was shown; like the other component names they are JSON keys, never renamed or removed. In the holding's
 * currency; a figure that needs something unknown is {@code null}.
 */
public record InvestorQuantityResponse(
        long investorId,
        BigDecimal quantity,
        BigDecimal sharesValue,
        BigDecimal sharesCost,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercent) {

    public InvestorQuantityResponse(long investorId, InvestorPart part) {
        this(investorId,
                part.quantity(),
                part.sharesValue(),
                part.sharesCost(),
                part.unrealizedPnl(),
                part.unrealizedPnlPercent());
    }
}
