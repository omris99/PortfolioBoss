import { Info } from 'lucide-react';
import { formatPercent } from '../../lib/format';
import { stocksAndCashOf } from './portfolioWeights';
import { CASH_COLOR_CLASS, STOCKS_COLOR_CLASS } from './WeightsCard';

/** A part of the bar, as long as its percent; nothing for a part of 0. */
function BarPart({ percent, colorClass }: { percent: number; colorClass: string }) {
  if (percent === 0) return null;
  return (
    <span
      className={`block h-full min-w-[2px] first:rounded-l-full last:rounded-r-full ${colorClass}`}
      style={{ flexGrow: percent, flexBasis: 0 }}
    />
  );
}

/** A part's name and percent, above its own end of the bar. */
function BarEndLabel({ name, percent }: { name: string; percent: number }) {
  return (
    <span>
      {name} <span className="text-[13px] font-semibold text-ink">{formatPercent(percent)}</span>
    </span>
  );
}

/**
 * At the bottom of the account's figures: how the positions value and the cash split their sum, as one thin bar — the
 * stocks from the left, the cash from the right, each named above its end. Cash below zero can't be a part of a whole,
 * so that is said in words instead; nothing at all while the cash is unknown.
 */
export function StocksAndCashBar({ positionsValue, cash }: { positionsValue: number; cash: number | null }) {
  if (cash !== null && cash < 0) {
    return (
      <p className="flex items-center gap-2 border-t px-6 pb-4 pt-3 text-[13px]">
        <Info size={14} className="shrink-0 text-muted" />
        The cash is below zero: part of the stocks is bought with borrowed money.
      </p>
    );
  }
  const stocksAndCash = stocksAndCashOf(positionsValue, cash);
  if (stocksAndCash === null) return null;

  const { stocksPercent, cashPercent } = stocksAndCash;
  return (
    <div
      role="img"
      aria-label={`Stocks ${formatPercent(stocksPercent)}, cash ${formatPercent(cashPercent)} of the whole account`}
      className="border-t px-6 pb-4 pt-3"
    >
      <div className="mb-1.5 flex justify-between text-caption text-muted" aria-hidden="true">
        <BarEndLabel name="Stocks" percent={stocksPercent} />
        <BarEndLabel name="Cash" percent={cashPercent} />
      </div>
      <div className="flex h-2 gap-0.5" aria-hidden="true">
        <BarPart percent={stocksPercent} colorClass={STOCKS_COLOR_CLASS} />
        <BarPart percent={cashPercent} colorClass={CASH_COLOR_CLASS} />
      </div>
    </div>
  );
}
