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

/** IB does not always report account-level figures; the API sends those as null. */
function formatOptionalMoney(amount: number | null): string {
  return amount === null ? '—' : formatMoney(amount);
}

function sumOfField(holdings: Holding[], field: 'marketValue' | 'unrealizedPnl'): number {
  return holdings.reduce((runningTotal, holding) => runningTotal + holding[field], 0);
}

export function SummaryBar({ snapshot }: { snapshot: PortfolioSnapshot }) {
  const totalPositionsValue = sumOfField(snapshot.holdings, 'marketValue');
  const totalUnrealizedPnl = sumOfField(snapshot.holdings, 'unrealizedPnl');

  return (
    <div
      className="flex flex-wrap items-center gap-x-8 gap-y-2 rounded-xl border border-slate-700/80 bg-slate-900/60 px-4 py-3"
      role="region"
      aria-label="Account summary"
    >
      <SummaryStat label="Net liquidation" value={formatOptionalMoney(snapshot.netLiquidation)} />
      <SummaryStat label="Cash" value={formatOptionalMoney(snapshot.totalCashValue)} />
      <SummaryStat label="Positions value" value={formatMoney(totalPositionsValue)} />
      <SummaryStat
        label="Unrealized P&L"
        value={formatSignedMoney(totalUnrealizedPnl)}
        valueColorClass={profitLossColorClass(totalUnrealizedPnl)}
      />
    </div>
  );
}
