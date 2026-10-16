package portfolioboss.api.response;

import portfolioboss.calculation.InvestorSummary;
import portfolioboss.db.InvestorEntity;
import portfolioboss.model.InvestorWarning;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One investor's summary card as the UI receives it: who they are, and the figures {@link InvestorSummary} holds and
 * derives — in USD, except the realized P&amp;L, which is by currency ({@code {"HKD": 950, "USD": 500}}). The component
 * names are the JSON keys and must stay stable. A figure that needs something unknown is {@code null}.
 *
 * @param depositsMinusWithdrawals {@code null} for the account owner, whose cash comes from IB
 * @param cashMovements            their deposits and withdrawals, by date; always empty for the account owner
 */
public record InvestorResponse(
        long id,
        String name,
        boolean accountOwner,
        BigDecimal depositsMinusWithdrawals,
        BigDecimal cash,
        BigDecimal sharesValue,
        BigDecimal totalValue,
        BigDecimal sharesCost,
        BigDecimal unrealizedPnl,
        BigDecimal unrealizedPnlPercent,
        Map<String, BigDecimal> realizedPnlByCurrency,
        BigDecimal totalPnl,
        List<CashMovementResponse> cashMovements,
        List<InvestorWarning> warnings) {

    public InvestorResponse(InvestorEntity investor, InvestorSummary summary) {
        this(investor.id(),
                investor.name(),
                investor.isAccountOwner(),
                summary.depositsMinusWithdrawals(),
                summary.cash(),
                summary.sharesValue(),
                summary.totalValue(),
                summary.sharesCost(),
                summary.unrealizedPnl(),
                summary.unrealizedPnlPercent(),
                summary.realizedPnlByCurrency(),
                summary.totalPnl(),
                investor.cashMovements().stream().map(CashMovementResponse::new).toList(),
                summary.warnings());
    }
}
