import type { KeyboardEvent, ReactNode } from 'react';
import { CARD_CLASS, tableRowClass } from '../../lib/styles';
import type { HotStock, HotStockLabel } from '../../types/recommendations';
import { useInvestors } from '../InvestorsContext';
import type { HotStockColumn } from './hotStockColumns';
import { LIST_WORDS, ListHeading } from './ListHeading';

// The lists' table: 13px cells on two lines (the figure, then its mark), top-aligned; the outer cells padded like the card.
const HEADER_CELL_CLASS =
  'h-9 border-b bg-subtle px-2 py-1.5 align-bottom text-label text-muted first:pl-5 last:pr-5';
const CELL_CLASS =
  'h-14 whitespace-nowrap border-b px-2 py-2 align-top first:pl-5 last:pr-5 [tr:last-child_&]:border-b-0';

function alignmentClassOf(column: HotStockColumn): string {
  return column.alignment === 'right' ? 'text-right' : 'text-left';
}

/**
 * A list's stocks, one row each, in the order the API gave — the page's order. A row (click or Enter) opens the stock's
 * drawer. `hasFixedWidths`: the columns keep their widths and the rest of the card stays empty on the right, so the
 * figures of the two short lists sit in the same place in both cards.
 */
function StockTable({
  columns,
  stocks,
  hasFixedWidths,
  openCandidateId,
  onOpen,
}: {
  columns: HotStockColumn[];
  stocks: HotStock[];
  hasFixedWidths: boolean;
  openCandidateId: number | null;
  onOpen: (candidateId: number) => void;
}) {
  const investors = useInvestors();
  const openOnEnter = (event: KeyboardEvent, candidateId: number) => {
    if (event.key === 'Enter') onOpen(candidateId);
  };
  return (
    <div className="overflow-x-auto">
      <table className={`w-full border-collapse text-[13px] ${hasFixedWidths ? 'table-fixed' : ''}`}>
        {hasFixedWidths && (
          <colgroup>
            {columns.map((column) => (
              <col key={column.key} className={column.widthClass} />
            ))}
            <col />
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
          </tr>
        </thead>
        <tbody>
          {stocks.map((stock) => (
            <tr
              key={stock.candidateId}
              tabIndex={0}
              onClick={() => onOpen(stock.candidateId)}
              onKeyDown={(event) => openOnEnter(event, stock.candidateId)}
              className={tableRowClass(stock.candidateId === openCandidateId)}
            >
              {columns.map((column) => (
                <td key={column.key} className={`${CELL_CLASS} ${alignmentClassOf(column)}`}>
                  {column.renderValue(stock, investors)}
                </td>
              ))}
              {hasFixedWidths && <td aria-hidden="true" className={CELL_CLASS} />}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** A list's card: its heading (`headingAside` at its right end), then its table — or one line when it is empty. */
export function StockListCard({
  list,
  stocks,
  columns,
  hasFixedWidths,
  openCandidateId,
  onOpen,
  headingAside,
}: {
  list: HotStockLabel;
  stocks: HotStock[];
  columns: HotStockColumn[];
  hasFixedWidths: boolean;
  openCandidateId: number | null;
  onOpen: (candidateId: number) => void;
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
        />
      )}
    </section>
  );
}
