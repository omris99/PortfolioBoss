import type { ReactNode } from 'react';
import { Info } from 'lucide-react';
import { formatPercent } from '../../lib/format';
import { SkeletonBar } from '../firstLoad/SkeletonBar';
import { stocksAndCashOf } from './portfolioWeights';
import { CASH_COLOR_CLASS, STOCKS_COLOR_CLASS } from './WeightsCard';

const BAR_FRAME_CLASS = 'border-t px-6 pb-4 pt-3';
const LABELS_ROW_CLASS = 'mb-1.5 flex justify-between text-caption text-muted';

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

/** A part's name and its percent — a grey bar while it loads —, above its own end of the bar. */
function BarEndLabel({ name, percent }: { name: string; percent: ReactNode }) {
  return (
    <span>
      {name} <span className="text-[13px] font-semibold text-ink">{percent}</span>
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
      className={BAR_FRAME_CLASS}
    >
      <div className={LABELS_ROW_CLASS} aria-hidden="true">
        <BarEndLabel name="Stocks" percent={formatPercent(stocksPercent)} />
        <BarEndLabel name="Cash" percent={formatPercent(cashPercent)} />
      </div>
      <div className="flex h-2 gap-0.5" aria-hidden="true">
        <BarPart percent={stocksPercent} colorClass={STOCKS_COLOR_CLASS} />
        <BarPart percent={cashPercent} colorClass={CASH_COLOR_CLASS} />
      </div>
    </div>
  );
}

/** The same bar during the first load: its two names, a grey bar for each percent, and the whole bar grey. */
export function StocksAndCashBarSkeleton() {
  return (
    <div className={BAR_FRAME_CLASS} aria-hidden="true">
      <div className={LABELS_ROW_CLASS}>
        <BarEndLabel name="Stocks" percent={<SkeletonBar width={40} height={10} />} />
        <BarEndLabel name="Cash" percent={<SkeletonBar width={40} height={10} />} />
      </div>
      <span className="block h-2 rounded-full bg-subtle" />
    </div>
  );
}
