import { formatPercent } from '../../lib/format';
import { stocksAndCashOf } from './portfolioWeights';
import { CASH_COLOR_CLASS, STOCKS_COLOR_CLASS } from './WeightsCard';

/** A part of the bar, as long as its percent; nothing for a part of 0. */
function BarPart({ percent, colorClass }: { percent: number; colorClass: string }) {
  if (percent === 0) return null;
  return (
    <span
      className={`h-full first:rounded-l-full last:rounded-r-full ${colorClass}`}
      style={{ flexGrow: percent, flexBasis: 0 }}
    />
  );
}

/** A part's name and percent, at its own end of the bar. */
function BarEndLabel({ name, percent }: { name: string; percent: number }) {
  return (
    <span className="text-muted">
      {name} <span className="font-semibold text-ink">{formatPercent(percent)}</span>
    </span>
  );
}

/**
 * Under the account's figures: how the positions value and the cash split their sum, as one bar — the stocks from the
 * left, the cash from the right, each named at its end. Cash below zero can't be a part of a whole, so that is said in
 * words instead; nothing at all while the cash is unknown.
 */
export function StocksAndCashBar({ positionsValue, cash }: { positionsValue: number; cash: number | null }) {
  if (cash !== null && cash < 0) {
    return (
      <p className="border-t px-6 py-3 text-caption text-muted">
        The cash is below zero: part of the stocks is bought with borrowed money.
      </p>
    );
  }
  const stocksAndCash = stocksAndCashOf(positionsValue, cash);
  if (stocksAndCash === null) return null;

  return (
    <div className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-4 border-t px-6 py-3">
      <BarEndLabel name="Stocks" percent={stocksAndCash.stocksPercent} />
      <div className="flex h-2.5 gap-0.5" aria-hidden="true">
        <BarPart percent={stocksAndCash.stocksPercent} colorClass={STOCKS_COLOR_CLASS} />
        <BarPart percent={stocksAndCash.cashPercent} colorClass={CASH_COLOR_CLASS} />
      </div>
      <BarEndLabel name="Cash" percent={stocksAndCash.cashPercent} />
    </div>
  );
}
