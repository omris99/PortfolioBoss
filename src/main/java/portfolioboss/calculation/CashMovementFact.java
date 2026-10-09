package portfolioboss.calculation;

import java.math.BigDecimal;

/**
 * One deposit or withdrawal, reduced to what {@link InvestorSummaryCalculator} needs: no date, no note. Built by
 * {@code InvestorEntity.cashMovementFacts()} so this class stays free of JPA, like {@link TradeFact}.
 *
 * @param amount always above 0: {@code type} says which way the money went
 */
public record CashMovementFact(long investorId, CashMovementType type, BigDecimal amount) {

    /** What it did to the investor's cash: positive for a deposit, negative for a withdrawal. */
    public BigDecimal signedAmount() {
        return type == CashMovementType.DEPOSIT ? amount : amount.negate();
    }
}
