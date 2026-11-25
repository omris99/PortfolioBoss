import type { ReactNode } from 'react';
import { formatMoney, formatSignedMoney, profitLossColorClass } from '../lib/format';
import { CARD_CLASS } from '../lib/styles';
import type { Holding, PortfolioSnapshot } from '../types/portfolio';
import { SkeletonBar } from './firstLoad/SkeletonBar';
import { StocksAndCashBar, StocksAndCashBarSkeleton } from './graphs/StocksAndCashBar';

/** The four figures' labels, the same while the figures load. */
const FIGURE_LABELS = {
  netLiquidation: 'Net liquidation',
  cash: 'Cash',
  positionsValue: 'Positions value',
  unrealizedPnl: 'Unrealized P&L',
} as const;

function SummaryFigure({
  label,
  value,
  valueColorClass = 'text-ink',
}: {
  label: string;
  value: ReactNode;
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
 * The four account figures at the top of the Overview, and under them how the stocks and the cash split the account.
 * The totals are over `openHoldings` only; the account-level figures come from the snapshot as IB reported them.
 */
export function AccountSummary({ snapshot, openHoldings }: { snapshot: PortfolioSnapshot; openHoldings: Holding[] }) {
  const totalPositionsValue = sumOfField(openHoldings, 'marketValue');
  const totalUnrealizedPnl = sumOfField(openHoldings, 'unrealizedPnl');

  return (
    <section className={CARD_CLASS} aria-label="Account summary">
      <div className="grid grid-cols-4 divide-x">
        <SummaryFigure label={FIGURE_LABELS.netLiquidation} value={formatMoney(snapshot.netLiquidation)} />
        <SummaryFigure label={FIGURE_LABELS.cash} value={formatMoney(snapshot.totalCashValue)} />
        <SummaryFigure label={FIGURE_LABELS.positionsValue} value={formatMoney(totalPositionsValue)} />
        <SummaryFigure
          label={FIGURE_LABELS.unrealizedPnl}
          value={formatSignedMoney(totalUnrealizedPnl)}
          valueColorClass={profitLossColorClass(totalUnrealizedPnl)}
        />
      </div>
      <StocksAndCashBar positionsValue={totalPositionsValue} cash={snapshot.totalCashValue} />
    </section>
  );
}

/** The same card during the first load: the four labels, a grey bar for each figure, and the stocks and cash bar grey. */
export function AccountSummarySkeleton() {
  return (
    <section className={CARD_CLASS} aria-label="Account summary" aria-busy="true">
      <div className="grid grid-cols-4 divide-x">
        {Object.values(FIGURE_LABELS).map((label) => (
          <SummaryFigure key={label} label={label} value={<SkeletonBar width={140} height={24} />} />
        ))}
      </div>
      <StocksAndCashBarSkeleton />
    </section>
  );
}
