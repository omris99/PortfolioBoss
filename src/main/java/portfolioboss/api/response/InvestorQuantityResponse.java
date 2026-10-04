package portfolioboss.api.response;

import java.math.BigDecimal;

/**
 * An investor's part of a holding's quantity, as the UI receives it in {@code HoldingResponse.investorQuantities}: 39
 * NVDA is {@code (1, 15)} and {@code (2, 24)}. See {@code PositionTrades.quantitiesByInvestor}.
 */
public record InvestorQuantityResponse(long investorId, BigDecimal quantity) {
}
