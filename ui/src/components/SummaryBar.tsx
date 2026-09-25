import { formatMoney, formatSignedMoney, profitLossColorClass } from '../lib/format';
import type { Holding, PortfolioSnapshot } from '../types/portfolio';

function SummaryStat({
  label,
  value,
  valueColorClass = 'text-slate-100',
}: {
  label: string;
  value: string;
  valueColorClass?: string;
}) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wide text-slate-500">{label}</div>
      <div className={`mt-0.5 font-mono text-sm font-semibold ${valueColorClass}`}>{value}</div>
    </div>
  );
}

/** A figure IB did not report for a holding counts as nothing in the total. */
function sumOfField(holdings: Holding[], field: 'marketValue' | 'unrealizedPnl'): number {
  return holdings.reduce((runningTotal, holding) => runningTotal + (holding[field] ?? 0), 0);
}

/** The totals are over `openHoldings` only; the account-level figures come from the snapshot as IB reported them. */
export function SummaryBar({ snapshot, openHoldings }: { snapshot: PortfolioSnapshot; openHoldings: Holding[] }) {
  const totalPositionsValue = sumOfField(openHoldings, 'marketValue');
  const totalUnrealizedPnl = sumOfField(openHoldings, 'unrealizedPnl');

  return (
    <div
      className="flex flex-wrap items-center gap-x-8 gap-y-2 rounded-xl border border-slate-700/80 bg-slate-900/60 px-4 py-3"
      role="region"
      aria-label="Account summary"
    >
      <SummaryStat label="Net liquidation" value={formatMoney(snapshot.netLiquidation)} />
      <SummaryStat label="Cash" value={formatMoney(snapshot.totalCashValue)} />
      <SummaryStat label="Positions value" value={formatMoney(totalPositionsValue)} />
      <SummaryStat
        label="Unrealized P&L"
        value={formatSignedMoney(totalUnrealizedPnl)}
        valueColorClass={profitLossColorClass(totalUnrealizedPnl)}
      />
    </div>
  );
}
