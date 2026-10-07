import type { ReactNode } from 'react';
import {
  EMPTY_VALUE,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import type { ClosedPosition, Holding, Investor, InvestorQuantity } from '../types/portfolio';
import { accountOwnerOf, hasSeveralInvestors, useInvestors } from './InvestorsContext';

const WITHOUT_REALIZED_PNL_HINT =
  'One of the closed positions here has no realized P&L: a price is missing, or more was sold than bought.';

/**
 * The position whose profit is divided: a holding, with IB's figures and each investor's part of them, or a manual
 * position, which IB never held — only its realized P&L divides.
 */
export type SplitPosition = { kind: 'holding'; holding: Holding } | { kind: 'manualPosition'; currency: string };

/** What a row shows of a holding: an investor's part of it, or all of it. */
type HoldingFigures = Omit<InvestorQuantity, 'investorId'>;

/** One row of the table: an investor, or the whole position. */
interface SplitRow {
  key: string;
  label: string;
  /** `null` while they hold none of it, and always for a manual position. */
  part: HoldingFigures | null;
  closedPositions: ClosedPosition[];
}

// ── the figures ─────────────────────────────────────────────────────────────────────────────────

function currencyOf(position: SplitPosition): string {
  return position.kind === 'holding' ? position.holding.currency : position.currency;
}

/**
 * Whoever has a part of the position, a trade in it or a closed position of it — in the order of the investor list, the
 * account owner first.
 */
function investorsInvolvedIn(
  position: SplitPosition,
  closedPositions: ClosedPosition[],
  investors: Investor[],
): Investor[] {
  const involvedIds = new Set(closedPositions.map((closedPosition) => closedPosition.investorId));
  if (position.kind === 'holding') {
    position.holding.investorQuantities.forEach((part) => involvedIds.add(part.investorId));
    position.holding.trades.forEach((trade) => involvedIds.add(trade.investorId));
  }
  return investors.filter((investor) => involvedIds.has(investor.id));
}

/**
 * Their sum; `null` if one of them has none — a sum of the others would read as the whole of it. The totals above the
 * closed positions table leave such a position out and count it instead; one cell has no room for that.
 */
function realizedPnlOf(closedPositions: ClosedPosition[]): number | null {
  let total = 0;
  for (const closedPosition of closedPositions) {
    if (closedPosition.realizedPnl === null) return null;
    total += closedPosition.realizedPnl;
  }
  return total;
}

function investorRowOf(investor: Investor, position: SplitPosition, closedPositions: ClosedPosition[]): SplitRow {
  const part =
    position.kind === 'holding'
      ? (position.holding.investorQuantities.find((investorPart) => investorPart.investorId === investor.id) ?? null)
      : null;
  return {
    key: `investor-${investor.id}`,
    label: investor.name,
    part,
    closedPositions: closedPositions.filter((closedPosition) => closedPosition.investorId === investor.id),
  };
}

/** IB's own figures for the holding, as in its row in Positions: the investors' parts add up to them. */
function totalRowOf(position: SplitPosition, closedPositions: ClosedPosition[]): SplitRow {
  const part: HoldingFigures | null =
    position.kind === 'holding'
      ? {
          quantity: position.holding.position,
          sharesCost: position.holding.costBasis,
          sharesValue: position.holding.marketValue,
          unrealizedPnl: position.holding.unrealizedPnl,
          unrealizedPnlPercent: position.holding.unrealizedPnlPercent,
        }
      : null;
  return { key: 'total', label: 'Total', part, closedPositions };
}

// ── the table ───────────────────────────────────────────────────────────────────────────────────

function HeaderCell({ alignment, children }: { alignment: 'left' | 'right'; children: ReactNode }) {
  return <th className={`px-2 py-1 font-medium ${alignment === 'right' ? 'text-right' : 'text-left'}`}>{children}</th>;
}

function FigureCell({ colorClass = '', children }: { colorClass?: string; children: ReactNode }) {
  return <td className={`px-2 py-1 text-right font-mono ${colorClass}`}>{children}</td>;
}

/** "—" when nothing of it was sold; "—" with a hint when a closed position has no realized P&L. */
function RealizedPnlCell({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  if (closedPositions.length === 0) return <FigureCell>{EMPTY_VALUE}</FigureCell>;
  const realizedPnl = realizedPnlOf(closedPositions);
  if (realizedPnl === null) {
    return (
      <FigureCell>
        <span title={WITHOUT_REALIZED_PNL_HINT}>{EMPTY_VALUE}</span>
      </FigureCell>
    );
  }
  return <FigureCell colorClass={profitLossColorClass(realizedPnl)}>{formatSignedMoney(realizedPnl)}</FigureCell>;
}

/** Quantity, cost, value and unrealized P&L: a holding's only. `null` part: they hold none of it any more. */
function HoldingFigureCells({ part }: { part: SplitRow['part'] }) {
  if (part === null) {
    return (
      <>
        <FigureCell>{formatQuantity(0)}</FigureCell>
        <FigureCell>{EMPTY_VALUE}</FigureCell>
        <FigureCell>{EMPTY_VALUE}</FigureCell>
        <FigureCell>{EMPTY_VALUE}</FigureCell>
        <FigureCell>{EMPTY_VALUE}</FigureCell>
      </>
    );
  }
  return (
    <>
      <FigureCell>{formatQuantity(part.quantity)}</FigureCell>
      <FigureCell>{formatMoney(part.sharesCost)}</FigureCell>
      <FigureCell>{formatMoney(part.sharesValue)}</FigureCell>
      <FigureCell colorClass={profitLossColorClass(part.unrealizedPnl)}>{formatSignedMoney(part.unrealizedPnl)}</FigureCell>
      <FigureCell colorClass={profitLossColorClass(part.unrealizedPnlPercent)}>
        {formatSignedPercent(part.unrealizedPnlPercent)}
      </FigureCell>
    </>
  );
}

function SplitTableRow({ row, isHolding, isTotal }: { row: SplitRow; isHolding: boolean; isTotal: boolean }) {
  const rowClass = isTotal ? 'border-t border-slate-700 font-semibold text-slate-100' : 'border-b border-slate-800/60';
  return (
    <tr className={rowClass}>
      <td className="px-2 py-1">{row.label}</td>
      {isHolding && <HoldingFigureCells part={row.part} />}
      <RealizedPnlCell closedPositions={row.closedPositions} />
    </tr>
  );
}

/**
 * Each investor's part of a position shared with another investor, and the whole of it in a last row — the unrealized
 * P&L of a holding and the realized P&L of the shares sold (INVESTORS_TODO.md, session 4). Shown only while an investor
 * other than the account owner has something in it; with the account owner alone there is nothing to divide.
 * `closedPositions` are the position's own, of every investor.
 */
export function InvestorSplitTable({
  position,
  closedPositions,
}: {
  position: SplitPosition;
  closedPositions: ClosedPosition[];
}) {
  const investors = useInvestors();
  const accountOwner = accountOwnerOf(investors);
  const involvedInvestors = investorsInvolvedIn(position, closedPositions, investors);
  const isShared = involvedInvestors.some((investor) => investor.id !== accountOwner?.id);
  if (!hasSeveralInvestors(investors) || !isShared) return null;

  const isHolding = position.kind === 'holding';
  const investorRows = involvedInvestors.map((investor) => investorRowOf(investor, position, closedPositions));
  return (
    <div className="flex flex-col gap-2 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
        By investor <span className="font-normal normal-case text-slate-500">· {currencyOf(position)}</span>
      </div>
      <table className="w-full border-collapse">
        <thead>
          <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-500">
            <HeaderCell alignment="left">Investor</HeaderCell>
            {isHolding && (
              <>
                <HeaderCell alignment="right">Qty</HeaderCell>
                <HeaderCell alignment="right">Cost</HeaderCell>
                <HeaderCell alignment="right">Value</HeaderCell>
                <HeaderCell alignment="right">Unrealized P&amp;L</HeaderCell>
                <HeaderCell alignment="right">%</HeaderCell>
              </>
            )}
            <HeaderCell alignment="right">Realized P&amp;L</HeaderCell>
          </tr>
        </thead>
        <tbody>
          {investorRows.map((row) => (
            <SplitTableRow key={row.key} row={row} isHolding={isHolding} isTotal={false} />
          ))}
          <SplitTableRow row={totalRowOf(position, closedPositions)} isHolding={isHolding} isTotal />
        </tbody>
      </table>
    </div>
  );
}
