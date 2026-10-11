import { formatMoney, formatSignedMoney, profitLossColorClass } from '../lib/format';
import { CARD_CLASS } from '../lib/styles';
import type { Holding, PortfolioSnapshot } from '../types/portfolio';

function SummaryFigure({
  label,
  value,
  valueColorClass = 'text-ink',
}: {
  label: string;
  value: string;
  valueColorClass?: string;
}) {
  return (
    <div className="px-6 py-5">
      <div className="text-label text-muted">{label}</div>
      <div className={`mt-2 text-figure-lg ${valueColorClass}`}>{value}</div>
    </div>
  );
}

/** A figure IB did not report for a holding counts as nothing in the total. */
function sumOfField(holdings: Holding[], field: 'marketValue' | 'unrealizedPnl'): number {
  return holdings.reduce((runningTotal, holding) => runningTotal + (holding[field] ?? 0), 0);
}

/**
 * The four account figures at the top of the Overview. The totals are over `openHoldings` only; the account-level
 * figures come from the snapshot as IB reported them.
 */
export function AccountSummary({ snapshot, openHoldings }: { snapshot: PortfolioSnapshot; openHoldings: Holding[] }) {
  const totalPositionsValue = sumOfField(openHoldings, 'marketValue');
  const totalUnrealizedPnl = sumOfField(openHoldings, 'unrealizedPnl');

  return (
    <section className={`grid grid-cols-4 divide-x ${CARD_CLASS}`} aria-label="Account summary">
      <SummaryFigure label="Net liquidation" value={formatMoney(snapshot.netLiquidation)} />
      <SummaryFigure label="Cash" value={formatMoney(snapshot.totalCashValue)} />
      <SummaryFigure label="Positions value" value={formatMoney(totalPositionsValue)} />
      <SummaryFigure
        label="Unrealized P&L"
        value={formatSignedMoney(totalUnrealizedPnl)}
        valueColorClass={profitLossColorClass(totalUnrealizedPnl)}
      />
    </section>
  );
}
