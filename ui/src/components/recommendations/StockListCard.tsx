import type { KeyboardEvent, ReactNode } from 'react';
import { CARD_CLASS, tableRowClass } from '../../lib/styles';
import type { HotStock, HotStockLabel } from '../../types/recommendations';
import { useInvestors } from '../InvestorsContext';
import type { HotStockColumn } from './hotStockColumns';
import { LIST_WORDS, ListHeading } from './ListHeading';

// The lists' table: 13px cells on two lines (the figure, then its mark), top-aligned; the outer cells padded like the card.
const HEADER_CELL_CLASS =
  'h-9 border-b bg-subtle px-2 py-1.5 align-bottom text-label text-muted first:pl-5 last:pr-5';
const CELL_CLASS = 'h-14 whitespace-nowrap px-2 py-2 align-top first:pl-5 last:pr-5';
/** A row's bottom rule — under its detail line instead, when it has one; none under the table's last row. */
const ROW_RULE_CLASS = 'border-b [tr:last-child_&]:border-b-0';
/** The detail line: close under the row it belongs to. */
const DETAIL_CELL_CLASS = `px-2 pb-2.5 align-top first:pl-5 last:pr-5 ${ROW_RULE_CLASS}`;
/** The last column, the Analyze button's. */
const ANALYZE_COLUMN_WIDTH_CLASS = 'w-[52px]';

function alignmentClassOf(column: HotStockColumn): string {
  return column.alignment === 'right' ? 'text-right' : 'text-left';
}

/** The detail line spans every column but the first (Stock) and the last (Analyze) — the empty filler too, if any. */
function detailSpanOf(columns: HotStockColumn[], hasFixedWidths: boolean): number {
  return columns.length - 1 + (hasFixedWidths ? 1 : 0);
}

/**
 * A row and its detail line hover and select together: hovering either one lights both (`:has(+ tr:hover)` lights the
 * row above the hovered line, `tr:hover + tr` the line under the hovered row).
 */
function rowClassOf(isSelected: boolean, hasDetailLine: boolean): string {
  const hoverWithLineClass = hasDetailLine && !isSelected ? '[&:has(+tr:hover)]:bg-subtle' : '';
  return `${tableRowClass(isSelected)} ${hoverWithLineClass}`;
}

function detailLineClassOf(isSelected: boolean): string {
  return isSelected ? 'cursor-pointer bg-selected' : 'cursor-pointer hover:bg-subtle [tr:hover+&]:bg-subtle';
}

/** One stock: its row, and under it the detail line when it has one. A click on either (or Enter on the row) opens it. */
function StockRows({
  stock,
  columns,
  hasFixedWidths,
  isSelected,
  detailLine,
  analyzeButton,
  onOpen,
}: {
  stock: HotStock;
  columns: HotStockColumn[];
  hasFixedWidths: boolean;
  isSelected: boolean;
  detailLine: ReactNode;
  analyzeButton: ReactNode;
  onOpen: (candidateId: number) => void;
}) {
  const investors = useInvestors();
  const hasDetailLine = detailLine !== null;
  const rowCellClass = `${CELL_CLASS} ${hasDetailLine ? '' : ROW_RULE_CLASS}`;
  const openOnEnter = (event: KeyboardEvent) => {
    if (event.key === 'Enter') onOpen(stock.candidateId);
  };
  return (
    <>
      <tr
        tabIndex={0}
        onClick={() => onOpen(stock.candidateId)}
        onKeyDown={openOnEnter}
        className={rowClassOf(isSelected, hasDetailLine)}
      >
        {columns.map((column) => (
          <td key={column.key} className={`${rowCellClass} ${alignmentClassOf(column)}`}>
            {column.renderValue(stock, investors)}
          </td>
        ))}
        {hasFixedWidths && <td aria-hidden="true" className={rowCellClass} />}
        <td className={`${rowCellClass} py-[14px] text-right`}>{analyzeButton}</td>
      </tr>
      {hasDetailLine && (
        <tr onClick={() => onOpen(stock.candidateId)} className={detailLineClassOf(isSelected)}>
          <td aria-hidden="true" className={DETAIL_CELL_CLASS} />
          <td colSpan={detailSpanOf(columns, hasFixedWidths)} className={DETAIL_CELL_CLASS}>
            {detailLine}
          </td>
          <td aria-hidden="true" className={DETAIL_CELL_CLASS} />
        </tr>
      )}
    </>
  );
}

/**
 * A list's stocks, one row each, in the order the API gave — the page's order. A row (click or Enter) opens the stock's
 * drawer; its last cell is its Analyze. `hasFixedWidths`: the columns keep their widths and the rest of the card stays
 * empty before the Analyze column, so the figures of the two short lists sit in the same place in both cards.
 * `detailLineOf`: the line under an analyzed row (`null` for the others).
 */
function StockTable({
  columns,
  stocks,
  hasFixedWidths,
  openCandidateId,
  onOpen,
  detailLineOf,
  analyzeButtonOf,
}: {
  columns: HotStockColumn[];
  stocks: HotStock[];
  hasFixedWidths: boolean;
  openCandidateId: number | null;
  onOpen: (candidateId: number) => void;
  detailLineOf: (stock: HotStock) => ReactNode;
  analyzeButtonOf: (stock: HotStock) => ReactNode;
}) {
  return (
    <div className="overflow-x-auto">
      <table className={`w-full border-collapse text-[13px] ${hasFixedWidths ? 'table-fixed' : ''}`}>
        {hasFixedWidths && (
          <colgroup>
            {columns.map((column) => (
              <col key={column.key} className={column.widthClass} />
            ))}
            <col />
            <col className={ANALYZE_COLUMN_WIDTH_CLASS} />
          </colgroup>
        )}
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col" className={`${HEADER_CELL_CLASS} ${alignmentClassOf(column)}`}>
                {column.title}
              </th>
            ))}
            {hasFixedWidths && <th aria-hidden="true" className={HEADER_CELL_CLASS} />}
            <th scope="col" className={`${HEADER_CELL_CLASS} ${ANALYZE_COLUMN_WIDTH_CLASS}`}>
              <span className="sr-only">Analyze</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {stocks.map((stock) => (
            <StockRows
              key={stock.candidateId}
              stock={stock}
              columns={columns}
              hasFixedWidths={hasFixedWidths}
              isSelected={stock.candidateId === openCandidateId}
              detailLine={detailLineOf(stock)}
              analyzeButton={analyzeButtonOf(stock)}
              onOpen={onOpen}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** No detail line: the short lists, which have none in the design. */
const NO_DETAIL_LINE = () => null;

/** A list's card: its heading (`headingAside` at its right end), then its table — or one line when it is empty. */
export function StockListCard({
  list,
  stocks,
  columns,
  hasFixedWidths,
  openCandidateId,
  onOpen,
  analyzeButtonOf,
  detailLineOf = NO_DETAIL_LINE,
  headingAside,
}: {
  list: HotStockLabel;
  stocks: HotStock[];
  columns: HotStockColumn[];
  hasFixedWidths: boolean;
  openCandidateId: number | null;
  onOpen: (candidateId: number) => void;
  analyzeButtonOf: (stock: HotStock) => ReactNode;
  detailLineOf?: (stock: HotStock) => ReactNode;
  headingAside?: ReactNode;
}) {
  const { title, emptyText } = LIST_WORDS[list];
  return (
    <section aria-label={title} className={`overflow-hidden ${CARD_CLASS}`}>
      <ListHeading list={list} count={stocks.length} aside={headingAside} />
      {stocks.length === 0 ? (
        <p className="px-5 py-6 text-center text-muted">{emptyText}</p>
      ) : (
        <StockTable
          columns={columns}
          stocks={stocks}
          hasFixedWidths={hasFixedWidths}
          openCandidateId={openCandidateId}
          onOpen={onOpen}
          detailLineOf={detailLineOf}
          analyzeButtonOf={analyzeButtonOf}
        />
      )}
    </section>
  );
}
