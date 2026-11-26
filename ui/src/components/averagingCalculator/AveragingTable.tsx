import { Info, TriangleAlert } from 'lucide-react';
import {
  ACCOUNT_CURRENCY,
  EMPTY_VALUE,
  formatMoney,
  formatPercent,
  formatQuantity,
  formatSignedPercentOneDecimal,
} from '../../lib/format';
import { ShortenedText } from '../ShortenedText';
import { SignalDot } from '../Signal';
import { percentOfAccountOf, type PurchasePlan } from '../weightCalculator/targetWeight';
import { isAmountWithinDigitLimit } from '../weightCalculator/ifIBuy';
import { NumberInputWithUnit, shareCountText } from '../weightCalculator/WeightCalculatorPanel';
import type { AveragingRow, RowResult, TypedTexts } from './averagingPlan';
import { WhatToKnowLine } from './WhatToKnowLine';

/** One holding of the table: its figures, what is typed on it, and what that comes to. */
export interface AveragingTableEntry {
  averagingRow: AveragingRow;
  typedTexts: TypedTexts;
  rowResult: RowResult;
}

/** The thin first header row: what the columns under each title are about (DESIGN_BRIEF.md §9, arrangement A). */
const COLUMN_GROUPS: { title: string; columnCount: number }[] = [
  { title: '', columnCount: 1 },
  { title: 'Now', columnCount: 4 },
  { title: 'Plan a purchase', columnCount: 2 },
  { title: 'After the purchase', columnCount: 4 },
];

/**
 * Every column's width is fixed, never measured from what is in it (2026-11-25, his note): measured, the empty Buy column
 * was 40px and a purchase typed made it 147px, taken from every other column, so the whole row moved while typing — and
 * the two sections' tables came out of different widths. Each width holds the widest text its column shows ("Already
 * reached", "120 shares ≈ 25,200.00", a field with its unit), and they add up to the table's 1134px at 1280–1440px; a
 * wider screen widens them all in the same proportion. Both sections use the same widths, so they line up.
 */
const COLUMNS: { title: string; isFigure: boolean; widthClass: string }[] = [
  { title: 'Holding', isFigure: false, widthClass: 'w-[150px]' },
  { title: 'Shares', isFigure: true, widthClass: 'w-[54px]' },
  { title: 'Average / Price', isFigure: true, widthClass: 'w-[122px]' },
  { title: 'To reach the average', isFigure: true, widthClass: 'w-[96px]' },
  { title: 'Weight', isFigure: true, widthClass: 'w-[54px]' },
  { title: 'Target weight', isFigure: false, widthClass: 'w-[107px]' },
  { title: 'Or buy for', isFigure: false, widthClass: 'w-[144px]' },
  { title: 'Buy', isFigure: true, widthClass: 'w-[155px]' },
  { title: 'New average', isFigure: true, widthClass: 'w-[74px]' },
  { title: 'To reach it', isFigure: true, widthClass: 'w-[108px]' },
  // Over 100% when a purchase is bigger than the account ("280.9%"; the cash card says it can't be bought).
  { title: 'Weight after', isFigure: true, widthClass: 'w-[70px]' },
];

/** Eleven columns at 1280px: narrower side padding than the other tables, and headers that wrap. */
const GROUP_HEADER_CELL_CLASS =
  'h-6 bg-subtle px-1.5 pt-1 text-left align-bottom text-[11px] font-semibold tracking-[0.02em] text-faint ' +
  'first:pl-4 last:pr-4';
const HEADER_CELL_CLASS =
  'border-b bg-subtle px-1.5 py-1.5 align-bottom text-label leading-[15px] text-muted first:pl-4 last:pr-4';
const ROW_CELL_CLASS = 'h-12 whitespace-nowrap px-1.5 align-middle text-[13px] first:pl-4 last:pr-4';
const LINE_CELL_CLASS = 'border-b px-1.5 pb-3 first:pl-4 last:pr-4 [tr:last-child_&]:border-b-0';

function alignmentClassOf(isFigure: boolean): string {
  return isFigure ? 'text-right' : 'text-left';
}

/** "+21.9%" below the average; "12.4% above" at or above it. */
function toReachTheAverageText(averagingRow: AveragingRow): string {
  if (averagingRow.isBelowAverage) return formatSignedPercentOneDecimal(averagingRow.riseNeededToReachAveragePercent);
  if (averagingRow.priceAboveAveragePercent < 0.05) return 'At the average';
  return `${formatPercent(averagingRow.priceAboveAveragePercent)} above`;
}

/**
 * The weight after the purchase; ">100%" past the whole account — only a purchase that costs more than all of it gets
 * there, which the cash card already says, and "11020.3%" ran past its column (2026-11-25, his OK).
 */
function weightAfterText(purchasePlan: PurchasePlan): string {
  return purchasePlan.percentAfter > 100 ? '>100%' : formatPercent(purchasePlan.percentAfter);
}

/** "+17.6%": how far the price must rise to reach the new average; "Already reached" when it is at or above it. */
function toReachTheNewAverageText(purchasePlan: PurchasePlan): string {
  const averageCostAfterPurchase = purchasePlan.averageCostAfterPurchase;
  if (averageCostAfterPurchase === null) return EMPTY_VALUE;
  const riseNeeded = averageCostAfterPurchase.riseNeededToReachNewAveragePercent;
  return riseNeeded > 0 ? formatSignedPercentOneDecimal(riseNeeded) : 'Already reached';
}

/** The columns' fixed widths, which the table takes from its first row (`table-fixed`). */
function ColumnWidths() {
  return (
    <colgroup>
      {COLUMNS.map((column) => (
        <col key={column.title} className={column.widthClass} />
      ))}
    </colgroup>
  );
}

function ColumnHeaders() {
  return (
    <thead>
      <tr>
        {COLUMN_GROUPS.map((columnGroup) => (
          <th key={columnGroup.title || 'holding'} colSpan={columnGroup.columnCount} className={GROUP_HEADER_CELL_CLASS}>
            {columnGroup.title !== '' && (
              <span className="block border-b border-strong pb-[3px]">{columnGroup.title}</span>
            )}
          </th>
        ))}
      </tr>
      <tr>
        {COLUMNS.map((column) => (
          <th key={column.title} className={`${HEADER_CELL_CLASS} ${alignmentClassOf(column.isFigure)}`}>
            {column.title}
          </th>
        ))}
      </tr>
    </thead>
  );
}

/** The signal dot, the symbol, and IB's sector under it — cut when long, whole in its hint. */
function HoldingCell({ averagingRow }: { averagingRow: AveragingRow }) {
  const { holding } = averagingRow;
  return (
    <span className="inline-flex min-w-0 items-center gap-2">
      <SignalDot holding={holding} />
      <span className="flex min-w-0 flex-col">
        <span className="font-semibold">{holding.symbol}</span>
        {holding.sector === null ? (
          <span className="text-caption text-muted">{EMPTY_VALUE}</span>
        ) : (
          <ShortenedText text={holding.sector} className="max-w-[122px] text-caption text-muted" />
        )}
      </span>
    </span>
  );
}

/** The four columns "After the purchase": empty until something typed buys. */
function PurchaseCells({ rowResult }: { rowResult: RowResult }) {
  const resultCellClass = `${ROW_CELL_CLASS} text-right`;
  if (rowResult.kind !== 'purchase') {
    return (
      <>
        <td className={resultCellClass} />
        <td className={resultCellClass} />
        <td className={resultCellClass} />
        <td className={resultCellClass} />
      </>
    );
  }
  const { purchasePlan } = rowResult;
  const newAverageCost = purchasePlan.averageCostAfterPurchase?.newAverageCost ?? null;
  return (
    <>
      <td className={resultCellClass}>
        {/* The column is sized for an amount of five digits: a bigger one goes under the shares, after the "≈". */}
        <span className="block whitespace-normal font-semibold">
          <span className="whitespace-nowrap">{shareCountText(purchasePlan.shareCount)} ≈</span>{' '}
          <span className="whitespace-nowrap">{formatMoney(purchasePlan.amount)}</span>
        </span>
        <span className="block text-caption text-muted">+ {formatMoney(purchasePlan.commission)} commission</span>
      </td>
      <td className={resultCellClass}>{formatMoney(newAverageCost)}</td>
      <td className={resultCellClass}>{toReachTheNewAverageText(purchasePlan)}</td>
      <td className={resultCellClass}>{weightAfterText(purchasePlan)}</td>
    </>
  );
}

/**
 * Above the line of what to know: why what is typed can't be used, in red — or, for a target the row can't buy toward,
 * why not, as plain information.
 */
function RowMessage({ rowResult }: { rowResult: RowResult }) {
  switch (rowResult.kind) {
    case 'unusable':
      return (
        <p role="alert" className="mb-1.5 flex items-center gap-1.5 text-caption font-medium text-negative">
          <TriangleAlert size={14} className="shrink-0" />
          {rowResult.reason}
        </p>
      );
    case 'atOrAboveTarget':
      return (
        <p className="mb-1.5 flex items-center gap-1.5 text-caption">
          <Info size={14} className="shrink-0 text-muted" />
          At or above the target already: this page only buys.
          <span className="text-faint">Selling stays in the holding’s drawer, on its Weight tab.</span>
        </p>
      );
    case 'alreadyAtTarget':
      return (
        <p className="mb-1.5 flex items-center gap-1.5 text-caption">
          <Info size={14} className="shrink-0 text-muted" />
          Already at the target: no whole share brings it closer to {formatPercent(rowResult.targetPercent)}.
        </p>
      );
    default:
      return null;
  }
}

/**
 * One holding: a row of figures and the two fields — typing in one empties the other —, then the line of what to know
 * under it. A row opens nothing: the drawer belongs to Positions, and going there would take the plan away.
 */
function AveragingTableRows({
  entry,
  onTypedTextsChange,
}: {
  entry: AveragingTableEntry;
  onTypedTextsChange: (typedTexts: TypedTexts) => void;
}) {
  const { averagingRow, typedTexts, rowResult } = entry;
  const { holding, holdingInAccount, averageCost } = averagingRow;
  // No edge marks a row that buys (round 6, the user's OK): the figures after the purchase and the cash card say it.
  const isFieldUnusable = (field: 'target' | 'amount') => rowResult.kind === 'unusable' && rowResult.field === field;
  const figureCellClass = `${ROW_CELL_CLASS} text-right`;
  return (
    <>
      <tr>
        <td className={ROW_CELL_CLASS}>
          <HoldingCell averagingRow={averagingRow} />
        </td>
        <td className={figureCellClass}>{formatQuantity(holdingInAccount.quantity)}</td>
        <td className={figureCellClass}>
          {formatMoney(averageCost)}
          <span className="text-faint"> / </span>
          {formatMoney(holdingInAccount.price)}
        </td>
        <td className={figureCellClass}>{toReachTheAverageText(averagingRow)}</td>
        <td className={figureCellClass}>{formatPercent(percentOfAccountOf(holdingInAccount))}</td>
        <td className={ROW_CELL_CLASS}>
          <NumberInputWithUnit
            typedText={typedTexts.targetText}
            unit="%"
            isUnusable={isFieldUnusable('target')}
            onTypedTextChange={(targetText) => onTypedTextsChange({ targetText, amountText: '' })}
            widthClass="w-[60px]"
            ariaLabel={`${holding.symbol} target weight, %`}
          />
        </td>
        <td className={ROW_CELL_CLASS}>
          <NumberInputWithUnit
            typedText={typedTexts.amountText}
            unit={ACCOUNT_CURRENCY}
            isUnusable={isFieldUnusable('amount')}
            onTypedTextChange={(amountText) => onTypedTextsChange({ targetText: '', amountText })}
            widthClass="w-[78px]"
            ariaLabel={`${holding.symbol} amount to buy for, ${ACCOUNT_CURRENCY}`}
            isTypedTextAllowed={isAmountWithinDigitLimit}
          />
        </td>
        <PurchaseCells rowResult={rowResult} />
      </tr>
      <tr>
        <td className={LINE_CELL_CLASS} />
        <td colSpan={COLUMNS.length - 1} className={LINE_CELL_CLASS}>
          <RowMessage rowResult={rowResult} />
          <WhatToKnowLine holding={holding} />
        </td>
      </tr>
    </>
  );
}

/**
 * The Averaging calculator's table (DESIGN_BRIEF.md §9, arrangement A): every figure in its own column, grouped Now ·
 * Plan a purchase · After the purchase, so the new averages and the weights after read straight down and compare; the
 * line of what to know under each holding.
 */
export function AveragingTable({
  entries,
  onTypedTextsChange,
}: {
  entries: AveragingTableEntry[];
  onTypedTextsChange: (holdingId: number, typedTexts: TypedTexts) => void;
}) {
  return (
    <table className="w-full table-fixed border-collapse">
      <ColumnWidths />
      <ColumnHeaders />
      <tbody>
        {entries.map((entry) => (
          <AveragingTableRows
            key={entry.averagingRow.holding.id}
            entry={entry}
            onTypedTextsChange={(typedTexts) => onTypedTextsChange(entry.averagingRow.holding.id, typedTexts)}
          />
        ))}
      </tbody>
    </table>
  );
}
