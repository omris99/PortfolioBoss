package portfolioboss.api.response;

import portfolioboss.calculation.CashMovementType;
import portfolioboss.db.InvestorCashMovementEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One deposit or withdrawal of an investor, as the UI receives it. {@code note} is {@code null} when not entered. */
public record CashMovementResponse(
        long id,
        LocalDate movementDate,
        CashMovementType type,
        BigDecimal amount,
        String note) {

    /** Public: {@code InvestorWriteService} answers with it too. */
    public CashMovementResponse(InvestorCashMovementEntity movement) {
        this(movement.id(), movement.movementDate(), movement.type(), movement.amount(), movement.note());
    }
}
