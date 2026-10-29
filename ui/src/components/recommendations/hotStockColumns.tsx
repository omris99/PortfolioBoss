import type { ReactNode } from 'react';
import { EMPTY_VALUE, formatMoney, formatPercent, formatSignedPercentOneDecimal } from '../../lib/format';
import { EXPLAINED_CLASS } from '../../lib/styles';
import type { Investor } from '../../types/portfolio';
import type { HotStock } from '../../types/recommendations';
import { MomentumChip } from '../Momentum';
import { TargetCell } from './AnalystTarget';
import {
  correlationsHint,
  correlationText,
  fromHighText,
  heldByText,
  NO_CORRELATION_REASON,
  yearReturnText,
} from './hotStockTexts';
import { InfoMark } from './InfoMark';
import { RecoveringTag } from './RiskSignTag';

/**
 * One column of a list's table: its header, its alignment, its width in the short lists (whose figures line up card to
 * card), and how it shows a stock. A cell has two lines: the figure, then the mark under it. A new column is one entry.
 */
export interface HotStockColumn {
  key: string;
  title: string;
  alignment: 'left' | 'right';
  widthClass: string;
  renderValue: (stock: HotStock, investors: Investor[]) => ReactNode;
}

/** The mark on a cell's second line. */
function MarkLine({ children }: { children: ReactNode }) {
  return <div className="mt-1">{children}</div>;
}

/** The symbol, and IB's name under it in small capitals, cut at 200px. */
function StockCell({ stock }: { stock: HotStock }) {
  return (
    <>
      <div className="text-body font-semibold">{stock.symbol}</div>
      <div title={stock.companyName ?? undefined} className="max-w-[200px] truncate text-[11px] uppercase text-muted">
        {stock.companyName ?? EMPTY_VALUE}
      </div>
    </>
  );
}

/** IB's two words on two lines: "Technology", then "Software" in muted. */
function SectorCell({ stock }: { stock: HotStock }) {
  if (stock.industry === null && stock.category === null) return <>{EMPTY_VALUE}</>;
  return (
    <>
      <div>{stock.industry ?? EMPTY_VALUE}</div>
      {stock.category !== null && <div className="text-muted">{stock.category}</div>}
    </>
  );
}

/** The holding it moves with most, its hover the top 3; Moves together under it from 0.7. */
function MovesWithCell({ stock }: { stock: HotStock }) {
  if (stock.correlations.length === 0) {
    return (
      <span title={NO_CORRELATION_REASON} className={EXPLAINED_CLASS}>
        {EMPTY_VALUE}
      </span>
    );
  }
  const strongestCorrelation = stock.correlations[0];
  return (
    <>
      <span title={correlationsHint(stock.correlations)} className={EXPLAINED_CLASS}>
        {correlationText(strongestCorrelation)}
      </span>
      {strongestCorrelation.movesTogether && (
        <MarkLine>
          <InfoMark kind="movesTogether" />
        </MarkLine>
      )}
    </>
  );
}

export const STOCK_COLUMN: HotStockColumn = {
  key: 'stock',
  title: 'Stock',
  alignment: 'left',
  widthClass: 'w-[220px]',
  renderValue: (stock) => <StockCell stock={stock} />,
};

export const SECTOR_COLUMN: HotStockColumn = {
  key: 'sector',
  title: 'Sector',
  alignment: 'left',
  widthClass: 'w-[150px]',
  renderValue: (stock) => <SectorCell stock={stock} />,
};

export const PRICE_COLUMN: HotStockColumn = {
  key: 'price',
  title: 'Price',
  alignment: 'right',
  widthClass: 'w-[96px]',
  renderValue: (stock) => formatMoney(stock.momentum.lastClose),
};

/** The analysts' target from Dow Jones (decision 14), known before any analysis: the upside, the firms under it. */
export const TARGET_COLUMN: HotStockColumn = {
  key: 'target',
  title: 'Analysts’ target',
  alignment: 'right',
  widthClass: 'w-[120px]',
  renderValue: (stock) => <TargetCell analystTarget={stock.analystTarget} />,
};

export const ABOVE_SMA50_COLUMN: HotStockColumn = {
  key: 'aboveSma50',
  title: 'Above 50-day',
  alignment: 'right',
  widthClass: 'w-[104px]',
  renderValue: (stock) => formatSignedPercentOneDecimal(stock.percentAboveSma50),
};

/** The same figure as Above 50-day, named for the Correcting list, where it is below 0: "−4.3%". */
export const UNDER_SMA50_COLUMN: HotStockColumn = {
  ...ABOVE_SMA50_COLUMN,
  key: 'underSma50',
  title: 'Under 50-day',
};

export const FROM_HIGH_COLUMN: HotStockColumn = {
  key: 'fromHigh',
  title: 'From the high',
  alignment: 'right',
  widthClass: 'w-[112px]',
  renderValue: (stock) => (
    <>
      {fromHighText(stock.momentum)}
      {stock.pulledBack && (
        <MarkLine>
          <InfoMark kind="pulledBack" />
        </MarkLine>
      )}
    </>
  ),
};

export const YEAR_COLUMN: HotStockColumn = {
  key: 'year',
  title: 'Year',
  alignment: 'right',
  widthClass: 'w-[136px]',
  renderValue: (stock) => (
    <>
      {yearReturnText(stock.momentum)}
      {stock.recovering === true && (
        <MarkLine>
          <RecoveringTag />
        </MarkLine>
      )}
    </>
  ),
};

export const BIGGEST_FALL_COLUMN: HotStockColumn = {
  key: 'biggestFall',
  title: 'Biggest fall',
  alignment: 'right',
  widthClass: 'w-[96px]',
  renderValue: (stock) => formatPercent(stock.momentum.maxDrawdownPercent),
};

export const MOMENTUM_COLUMN: HotStockColumn = {
  key: 'momentum',
  title: 'Momentum',
  alignment: 'left',
  widthClass: 'w-[120px]',
  renderValue: (stock) => <MomentumChip momentum={stock.momentum} />,
};

export const MOVES_WITH_COLUMN: HotStockColumn = {
  key: 'movesWith',
  title: 'Moves with',
  alignment: 'left',
  widthClass: 'w-[128px]',
  renderValue: (stock) => <MovesWithCell stock={stock} />,
};

export const IN_PORTFOLIO_COLUMN: HotStockColumn = {
  key: 'inPortfolio',
  title: 'In the portfolio',
  alignment: 'left',
  widthClass: 'w-[112px]',
  renderValue: (stock, investors) => heldByText(stock, investors),
};

/** The Hot table's columns, in their order (DESIGN_BRIEF.md §8.2). */
export const HOT_COLUMNS: HotStockColumn[] = [
  STOCK_COLUMN,
  SECTOR_COLUMN,
  PRICE_COLUMN,
  TARGET_COLUMN,
  ABOVE_SMA50_COLUMN,
  FROM_HIGH_COLUMN,
  YEAR_COLUMN,
  BIGGEST_FALL_COLUMN,
  MOMENTUM_COLUMN,
  MOVES_WITH_COLUMN,
  IN_PORTFOLIO_COLUMN,
];

export const TOO_HOT_COLUMNS: HotStockColumn[] = [
  STOCK_COLUMN,
  PRICE_COLUMN,
  TARGET_COLUMN,
  ABOVE_SMA50_COLUMN,
  YEAR_COLUMN,
  MOMENTUM_COLUMN,
];

export const CORRECTING_COLUMNS: HotStockColumn[] = [
  STOCK_COLUMN,
  PRICE_COLUMN,
  TARGET_COLUMN,
  UNDER_SMA50_COLUMN,
  FROM_HIGH_COLUMN,
  YEAR_COLUMN,
  MOMENTUM_COLUMN,
];
