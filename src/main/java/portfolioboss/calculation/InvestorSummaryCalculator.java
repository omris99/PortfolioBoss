package portfolioboss.calculation;

import portfolioboss.utils.Utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works out every investor's summary card ({@link InvestorSummary}): their cash, the value and cost of their shares, and
 * their profit (INVESTORS_TODO.md). Every investor other than the account owner is worked out from what was entered for
 * them — deposits, withdrawals and trades — and the account owner gets whatever IB reports beyond that, so the investors
 * always add up to IB's own figures, even while trades are missing. Pure computation, like {@link HoldingHistory}.
 *
 * <p>Example: IB reports 40,000 in cash. The other investor deposited 30,000 and bought 24 NVDA at 120: their cash is
 * 27,120, and the account owner's the 12,880 left.
 *
 * @param accountOwnerId   the investor who gets what the others' entries don't explain
 * @param investorIds      every investor, the account owner too: one with deposits and no trade yet still has a card
 * @param positions        every holding and every manual position, with their trades
 * @param cashMovements    the deposits and withdrawals of every investor
 * @param ibCash           IB's {@code TotalCashValue}, or {@code null} if IB did not report it
 * @param ibNetLiquidation IB's {@code NetLiquidation}, or {@code null} if IB did not report it
 */
public record InvestorSummaryCalculator(long accountOwnerId, List<Long> investorIds, List<PositionTrades> positions,
                                        List<CashMovementFact> cashMovements, Double ibCash,
                                        Double ibNetLiquidation) {

    /** Every investor's card, by id. The other investors' come first: the account owner's is what they leave. */
    public Map<Long, InvestorSummary> summariesByInvestor() {
        Map<Long, InvestorSummary> summaries = new TreeMap<>();
        for (long investorId : otherInvestorIds()) {
            summaries.put(investorId, otherInvestorSummary(investorId));
        }
        summaries.put(accountOwnerId, accountOwnerSummary(List.copyOf(summaries.values())));
        return summaries;
    }

    // ── an investor other than the account owner: what was entered for them ─────────────────────────────

    private InvestorSummary otherInvestorSummary(long investorId) {
        BigDecimal depositsMinusWithdrawals = depositsMinusWithdrawalsOf(investorId);
        BigDecimal cash = cashOf(investorId, depositsMinusWithdrawals);
        BigDecimal sharesValue = sharesValueOf(investorId);
        return new InvestorSummary(depositsMinusWithdrawals, cash, sharesValue, sumOrNull(cash, sharesValue),
                sharesCostOf(investorId), realizedPnlByCurrencyOf(investorId), otherInvestorWarnings(investorId, cash));
    }

    private BigDecimal depositsMinusWithdrawalsOf(long investorId) {
        return cashMovements.stream()
                .filter(movement -> movement.investorId() == investorId)
                .map(CashMovementFact::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * What they put in, less what their buys in USD took out, plus what their sells brought in, commissions included
     * (INVESTORS_TODO.md, decision 4). {@code null} if one of those trades has no price.
     */
    private BigDecimal cashOf(long investorId, BigDecimal depositsMinusWithdrawals) {
        BigDecimal cash = depositsMinusWithdrawals;
        for (TradeFact trade : accountCurrencyTradesOf(investorId)) {
            cash = sumOrNull(cash, trade.cashFlow());
        }
        return cash;
    }

    /**
     * Their shares in every holding in USD, at IB's market price — the same figure as their part of each holding
     * ({@link PositionTrades#partsByInvestor}). A manual position has none: IB never held it.
     */
    private BigDecimal sharesValueOf(long investorId) {
        BigDecimal sharesValue = BigDecimal.ZERO;
        for (PositionTrades holding : accountCurrencyHoldings()) {
            sharesValue = sumOrNull(sharesValue, holding.sharesValueOf(investorId));
        }
        return sharesValue;
    }

    /** What the shares they still hold cost them, in every holding in USD, at average cost. */
    private BigDecimal sharesCostOf(long investorId) {
        BigDecimal sharesCost = BigDecimal.ZERO;
        for (PositionTrades holding : accountCurrencyHoldings()) {
            sharesCost = sumOrNull(sharesCost, holding.sharesCostOf(investorId));
        }
        return sharesCost;
    }

    private List<InvestorWarning> otherInvestorWarnings(long investorId, BigDecimal cash) {
        List<InvestorWarning> warnings = new ArrayList<>();
        tradesWithoutPriceWarning(investorId).ifPresent(warnings::add);
        tradesNotInUsdWarning(investorId).ifPresent(warnings::add);
        negativeCashWarning(cash).ifPresent(warnings::add);
        warnings.addAll(moreSharesThanIbWarnings(investorId));
        closedPositionsNotCountedWarning(investorId).ifPresent(warnings::add);
        return warnings;
    }

    private Optional<InvestorWarning> tradesWithoutPriceWarning(long investorId) {
        long tradesWithoutPrice = accountCurrencyTradesOf(investorId).stream()
                .filter(trade -> trade.price() == null)
                .count();
        if (tradesWithoutPrice == 0) {
            return Optional.empty();
        }
        return Optional.of(new InvestorWarning(InvestorWarningType.TRADES_WITHOUT_PRICE,
                "No price on %s, so the cash is unknown.".formatted(countOf(tradesWithoutPrice, "trade"))));
    }

    private Optional<InvestorWarning> tradesNotInUsdWarning(long investorId) {
        List<PositionTrades> otherCurrencyPositions = positions.stream()
                .filter(position -> !position.isInAccountCurrency())
                .filter(position -> !position.tradesOf(investorId).isEmpty())
                .toList();
        if (otherCurrencyPositions.isEmpty()) {
            return Optional.empty();
        }
        long tradesLeftOut = otherCurrencyPositions.stream()
                .mapToLong(position -> position.tradesOf(investorId).size())
                .sum();
        String currencies = otherCurrencyPositions.stream()
                .map(PositionTrades::currency)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        return Optional.of(new InvestorWarning(InvestorWarningType.TRADES_NOT_IN_USD,
                "Left out of the cash, which counts USD only: %s in %s."
                        .formatted(countOf(tradesLeftOut, "trade"), currencies)));
    }

    private Optional<InvestorWarning> negativeCashWarning(BigDecimal cash) {
        if (cash == null || cash.signum() >= 0) {
            return Optional.empty();
        }
        // Locale.ROOT: "-2,880.00" on every machine, whatever its language settings
        return Optional.of(new InvestorWarning(InvestorWarningType.NEGATIVE_CASH,
                String.format(Locale.ROOT, "The cash comes to %,.2f USD: a deposit may be missing.", cash)));
    }

    /**
     * One for every holding where their trades add up to more shares than IB reports for the whole of it — and for every
     * manual position where they still hold some, since IB holds none of it.
     */
    private List<InvestorWarning> moreSharesThanIbWarnings(long investorId) {
        List<InvestorWarning> warnings = new ArrayList<>();
        for (PositionTrades position : positions) {
            BigDecimal quantity = position.quantityOf(investorId);
            BigDecimal ibQuantity = position.ibQuantity();
            boolean holdsMoreThanIb = ibQuantity != null
                    && quantity.subtract(ibQuantity).compareTo(HoldingHistory.QUANTITY_TOLERANCE) > 0;
            if (holdsMoreThanIb) {
                warnings.add(new InvestorWarning(InvestorWarningType.MORE_SHARES_THAN_IB,
                        "The trades in %s add up to %s shares; IB reports %s.".formatted(position.symbol(),
                                HoldingHistory.plainNumber(quantity), HoldingHistory.plainNumber(ibQuantity))));
            }
        }
        return warnings;
    }

    // ── the account owner: what IB reports, less the other investors' part ──────────────────────────────

    /** Their cash, shares, total value and cost are IB's less the others'; their realized P&amp;L is their own. */
    private InvestorSummary accountOwnerSummary(List<InvestorSummary> otherSummaries) {
        List<InvestorWarning> warnings = new ArrayList<>();
        closedPositionsNotCountedWarning(accountOwnerId).ifPresent(warnings::add);
        return new InvestorSummary(
                null,
                whatOthersLeaveOf(Utils.decimalOrNull(ibCash), otherSummaries, InvestorSummary::cash),
                whatOthersLeaveOf(ibSharesValue(), otherSummaries, InvestorSummary::sharesValue),
                whatOthersLeaveOf(Utils.decimalOrNull(ibNetLiquidation), otherSummaries, InvestorSummary::totalValue),
                whatOthersLeaveOf(ibSharesCost(), otherSummaries, InvestorSummary::sharesCost),
                realizedPnlByCurrencyOf(accountOwnerId),
                warnings);
    }

    /** IB's figure for the whole account less the other investors' part of it; {@code null} if any of them is unknown. */
    private BigDecimal whatOthersLeaveOf(BigDecimal ibFigure, List<InvestorSummary> otherSummaries,
                                         Function<InvestorSummary, BigDecimal> figure) {
        BigDecimal whatIsLeft = ibFigure;
        for (InvestorSummary otherSummary : otherSummaries) {
            whatIsLeft = subtractOrNull(whatIsLeft, figure.apply(otherSummary));
        }
        return whatIsLeft;
    }

    /** Every holding in USD at IB's market value — 0 for a {@code CLOSED} one. */
    private BigDecimal ibSharesValue() {
        BigDecimal sharesValue = BigDecimal.ZERO;
        for (PositionTrades holding : accountCurrencyHoldings()) {
            sharesValue = sumOrNull(sharesValue, holding.ibMarketValue());
        }
        return sharesValue;
    }

    /** Every holding in USD at IB's cost, commissions included. */
    private BigDecimal ibSharesCost() {
        BigDecimal sharesCost = BigDecimal.ZERO;
        for (PositionTrades holding : accountCurrencyHoldings()) {
            sharesCost = sumOrNull(sharesCost, holding.ibCostBasis());
        }
        return sharesCost;
    }

    // ── shared by every investor ────────────────────────────────────────────────────────────────────────

    /** Of their own closed positions, in holdings and manual positions alike; those without one are left out. */
    private Map<String, BigDecimal> realizedPnlByCurrencyOf(long investorId) {
        Map<String, BigDecimal> realizedPnlByCurrency = new TreeMap<>();
        for (PositionTrades position : positions) {
            for (ClosedPosition closedPosition : position.historyOf(investorId).closedPositions()) {
                if (closedPosition.realizedPnl() != null) {
                    realizedPnlByCurrency.merge(position.currency(), closedPosition.realizedPnl(), BigDecimal::add);
                }
            }
        }
        return realizedPnlByCurrency;
    }

    private Optional<InvestorWarning> closedPositionsNotCountedWarning(long investorId) {
        long closedPositionsNotCounted = positions.stream()
                .flatMap(position -> position.historyOf(investorId).closedPositions().stream())
                .filter(closedPosition -> closedPosition.realizedPnl() == null)
                .count();
        if (closedPositionsNotCounted == 0) {
            return Optional.empty();
        }
        return Optional.of(new InvestorWarning(InvestorWarningType.CLOSED_POSITIONS_NOT_COUNTED,
                "Left out of the realized P&L: %s without one (a price is missing, or more was sold than bought)."
                        .formatted(countOf(closedPositionsNotCounted, "closed position"))));
    }

    private List<Long> otherInvestorIds() {
        return investorIds.stream().filter(investorId -> investorId != accountOwnerId).toList();
    }

    private List<PositionTrades> accountCurrencyHoldings() {
        return positions.stream()
                .filter(PositionTrades::isHolding)
                .filter(PositionTrades::isInAccountCurrency)
                .toList();
    }

    /** Their trades in USD, in holdings and manual positions alike. */
    private List<TradeFact> accountCurrencyTradesOf(long investorId) {
        return positions.stream()
                .filter(PositionTrades::isInAccountCurrency)
                .flatMap(position -> position.tradesOf(investorId).stream())
                .toList();
    }

    /** "1 trade", "3 trades". */
    private String countOf(long count, String thing) {
        return count == 1 ? "1 " + thing : count + " " + thing + "s";
    }

    private BigDecimal sumOrNull(BigDecimal first, BigDecimal second) {
        return first == null || second == null ? null : first.add(second);
    }

    private BigDecimal subtractOrNull(BigDecimal from, BigDecimal amount) {
        return from == null || amount == null ? null : from.subtract(amount);
    }
}
