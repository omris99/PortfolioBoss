import { useState, type ReactNode } from 'react';
import { ArrowRight, Pencil, Plus, Trash2 } from 'lucide-react';
import { Link } from 'react-router';
import { deleteTrade } from '../lib/apiClient';
import { EMPTY_VALUE, formatMoney, formatQuantity } from '../lib/format';
import {
  DELETE_ICON_BUTTON_CLASS,
  ICON_BUTTON_CLASS,
  INNER_CELL_ABOVE_ITS_LINE_CLASS,
  INNER_CELL_CLASS,
  INNER_HEADER_CELL_CLASS,
  INNER_TABLE_CLASS,
  INNER_TABLE_FRAME_CLASS,
  SMALL_PRIMARY_BUTTON_CLASS,
  SMALL_SECONDARY_BUTTON_CLASS,
  sideTextClass,
} from '../lib/styles';
import type { StockSplit, Trade, TranslatedTrade } from '../types/portfolio';
import { InfoBand } from './FormFields';
import { HoldingWarningBands } from './HoldingWarnings';
import { hasSeveralInvestors, investorNameOf, useInvestors } from './InvestorsContext';
import { ConfirmDialog } from './Modal';
import { isBeforeTheSplit } from './stockSplits/sharesAsTheyWereThen';
import { StockSplitRow } from './stockSplits/StockSplitRow';
import { tradeBeforeTheSplitText } from './stockSplits/stockSplitTexts';
import { TradeBeforeTheSplitRow } from './stockSplits/TradeBeforeTheSplitRow';
import { backfilledHoldingOf, stockSplitsOf, symbolOf, TradeModal, type TradeOwner } from './TradeModal';

/** What the ✏️ and 🗑 of each row do. A table without them is read-only. */
interface TradeRowActions {
  onEdit: (trade: Trade) => void;
  onDelete: (trade: Trade) => void;
}

/** How a split translated the trade `tradeId`, or `null` when no split did. */
function translatedTradeOf(translatedTrades: TranslatedTrade[], tradeId: number): TranslatedTrade | null {
  return translatedTrades.find((translatedTrade) => translatedTrade.tradeId === tradeId) ?? null;
}

/**
 * The quantity and price a trade's row shows: as they count after the splits for a trade a split translated — like
 * IB's quantity and every figure above the table —, as typed for any other.
 */
function quantityAndPriceAfterTheSplits(
  trade: Trade,
  translatedTrade: TranslatedTrade | null,
): { quantity: number; price: number } {
  if (translatedTrade === null) return { quantity: trade.quantity, price: trade.price };
  return { quantity: translatedTrade.quantity, price: translatedTrade.price };
}

/** One trade's row — and, for a trade a split translated, the line under it with what was typed. */
function TradeRow({
  trade,
  translatedTrade,
  investorName,
  rowActions,
  columnCount,
}: {
  trade: Trade;
  translatedTrade: TranslatedTrade | null;
  /** `null` while the account owner is the only investor: the column is then left out. */
  investorName: string | null;
  rowActions: TradeRowActions | null;
  columnCount: number;
}) {
  const { quantity, price } = quantityAndPriceAfterTheSplits(trade, translatedTrade);
  const cellClass = translatedTrade === null ? INNER_CELL_CLASS : INNER_CELL_ABOVE_ITS_LINE_CLASS;
  return (
    <>
      <tr>
        <td className={cellClass}>{trade.tradeDate}</td>
        <td className={cellClass}>
          <span className={sideTextClass(trade.side === 'SELL')}>{trade.side}</span>
        </td>
        <td className={`${cellClass} text-right`}>{formatQuantity(quantity)}</td>
        <td className={`${cellClass} text-right`}>{formatMoney(price)}</td>
        <td className={`${cellClass} text-right`}>{formatMoney(trade.commission)}</td>
        <td title={trade.note ?? undefined} className={`${cellClass} max-w-[120px] truncate`}>
          {trade.note ?? <span className="text-faint">{EMPTY_VALUE}</span>}
        </td>
        {investorName !== null && <td className={cellClass}>{investorName}</td>}
        {rowActions !== null && (
          <td className={`${cellClass} w-14 text-right`}>
            <span className="inline-flex">
              <button
                type="button"
                aria-label="Edit trade"
                title="Edit this trade"
                onClick={() => rowActions.onEdit(trade)}
                className={ICON_BUTTON_CLASS}
              >
                <Pencil size={14} />
              </button>
              <button
                type="button"
                aria-label="Delete trade"
                title="Delete this trade"
                onClick={() => rowActions.onDelete(trade)}
                className={DELETE_ICON_BUTTON_CLASS}
              >
                <Trash2 size={14} />
              </button>
            </span>
          </td>
        )}
      </tr>
      {translatedTrade !== null && <TradeBeforeTheSplitRow trade={trade} columnCount={columnCount} />}
    </>
  );
}

/** One row of the trades table: a trade, or a split at its date. */
type TradeTableRow = { kind: 'trade'; trade: Trade } | { kind: 'stockSplit'; stockSplit: StockSplit };

/**
 * The trades and the splits in one list, by date (both come earliest first). A split comes after the trades of its own
 * day: IB books it after that day's market closed, so they were before it.
 */
function tradesAndSplitsInDateOrder(trades: Trade[], stockSplits: StockSplit[]): TradeTableRow[] {
  const rows: TradeTableRow[] = [];
  let splitsPlacedCount = 0;
  for (const trade of trades) {
    while (
      splitsPlacedCount < stockSplits.length &&
      !isBeforeTheSplit(trade.tradeDate, stockSplits[splitsPlacedCount])
    ) {
      rows.push({ kind: 'stockSplit', stockSplit: stockSplits[splitsPlacedCount] });
      splitsPlacedCount += 1;
    }
    rows.push({ kind: 'trade', trade });
  }
  for (const stockSplit of stockSplits.slice(splitsPlacedCount)) {
    rows.push({ kind: 'stockSplit', stockSplit });
  }
  return rows;
}

/**
 * The trades as a compact table, by date, with `stockSplits` between them at their dates. With `rowActions` every
 * trade can be corrected and deleted; without, it is read-only. Whose each trade is shows in a column once there is
 * more than one investor.
 */
function TradeTable({
  trades,
  stockSplits,
  translatedTrades,
  rowActions = null,
}: {
  trades: Trade[];
  stockSplits: StockSplit[];
  translatedTrades: TranslatedTrade[];
  rowActions?: TradeRowActions | null;
}) {
  const investors = useInvestors();
  const showsInvestor = hasSeveralInvestors(investors);
  const headers: { title: string; isFigure?: boolean }[] = [
    { title: 'Date' },
    { title: 'Side' },
    { title: 'Qty', isFigure: true },
    { title: 'Price', isFigure: true },
    { title: 'Commission', isFigure: true },
    { title: 'Note' },
    ...(showsInvestor ? [{ title: 'Investor' }] : []),
  ];
  const columnCount = headers.length + (rowActions === null ? 0 : 1);
  return (
    <div className={INNER_TABLE_FRAME_CLASS}>
      <table className={INNER_TABLE_CLASS}>
        <thead>
          <tr>
            {headers.map((header) => (
              <th
                key={header.title}
                className={`${INNER_HEADER_CELL_CLASS} font-medium ${header.isFigure ? 'text-right' : ''}`}
              >
                {header.title}
              </th>
            ))}
            {rowActions !== null && (
              <th className={INNER_HEADER_CELL_CLASS}>
                <span className="sr-only">Actions</span>
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {tradesAndSplitsInDateOrder(trades, stockSplits).map((row) =>
            row.kind === 'stockSplit' ? (
              <StockSplitRow
                key={`split-${row.stockSplit.splitDay}`}
                stockSplit={row.stockSplit}
                columnCount={columnCount}
              />
            ) : (
              <TradeRow
                key={`trade-${row.trade.id}`}
                trade={row.trade}
                translatedTrade={translatedTradeOf(translatedTrades, row.trade.id)}
                investorName={showsInvestor ? investorNameOf(investors, row.trade.investorId) : null}
                rowActions={rowActions}
                columnCount={columnCount}
              />
            ),
          )}
        </tbody>
      </table>
    </div>
  );
}

function tradesOf(owner: TradeOwner): Trade[] {
  return owner.kind === 'holding' ? owner.holding.trades : owner.trades;
}

/** The trades a split translated: a holding's; a manual position has none — no contract, sold before the first sync. */
function translatedTradesOf(owner: TradeOwner): TranslatedTrade[] {
  return owner.kind === 'holding' ? owner.holding.translatedTrades : [];
}

/**
 * "2026-08-14 · SELL 10 AMZN at 205.00 · Me": the trade being deleted, in one line, as its row shows it — with what
 * was typed when a split translated it: "BUY 40 ABCD at 100.00 (10 at 400.00 before the split)".
 */
function tradeLineOf(
  trade: Trade,
  translatedTrade: TranslatedTrade | null,
  symbol: string,
  investorName: string | null,
): string {
  const { quantity, price } = quantityAndPriceAfterTheSplits(trade, translatedTrade);
  const beforeTheSplit = translatedTrade === null ? '' : ` (${tradeBeforeTheSplitText(trade)})`;
  const investor = investorName === null ? '' : ` · ${investorName}`;
  return `${trade.tradeDate} · ${trade.side} ${formatQuantity(quantity)} ${symbol} at ${formatMoney(price)}${beforeTheSplit}${investor}`;
}

/** No trades yet: for a holding being backfilled, what the form will start as. */
function NoTradesYet({ owner, addButton }: { owner: TradeOwner; addButton: ReactNode }) {
  const backfilledHolding = backfilledHoldingOf(owner, null);
  return (
    <div className="py-6 text-muted">
      <p className="mb-3 text-ink">No trades entered yet.</p>
      {backfilledHolding !== null && (
        <p className="mb-3">
          The form starts filled in as a BUY of the whole position ({formatQuantity(backfilledHolding.position)}) at
          IB’s average cost, {formatMoney(backfilledHolding.averageCost)}.
        </p>
      )}
      {addButton}
    </div>
  );
}

/** The trade being added or corrected in the modal, or none. */
type TradeModalState = { tradeBeingEdited: Trade | null } | null;

/**
 * A drawer's Trades tab: the holding's warning again, its trades — or a manual position's — with ✏️ and 🗑, and Add
 * trade. Adding and correcting open the trade modal; deleting asks first. Every change reloads the whole portfolio,
 * since the dates, holding period and closed positions are derived from these trades on the server.
 */
export function TradesPanel({ owner, onDataChanged }: { owner: TradeOwner; onDataChanged: () => Promise<void> }) {
  const investors = useInvestors();
  const [tradeModal, setTradeModal] = useState<TradeModalState>(null);
  const [tradeToDelete, setTradeToDelete] = useState<Trade | null>(null);
  const trades = tradesOf(owner);
  const translatedTrades = translatedTradesOf(owner);

  const rowActions: TradeRowActions = {
    onEdit: (trade) => setTradeModal({ tradeBeingEdited: trade }),
    onDelete: setTradeToDelete,
  };
  const addButton = (
    <button
      type="button"
      onClick={() => setTradeModal({ tradeBeingEdited: null })}
      className={trades.length === 0 ? SMALL_PRIMARY_BUTTON_CLASS : SMALL_SECONDARY_BUTTON_CLASS}
    >
      <Plus size={14} />
      Add trade
    </button>
  );
  const deleteTradeAndReload = async (trade: Trade) => {
    await deleteTrade(trade.id);
    await onDataChanged();
  };

  return (
    <div>
      {owner.kind === 'holding' && owner.holding.warnings.length > 0 && (
        <div className="mb-4">
          <HoldingWarningBands warnings={owner.holding.warnings} />
        </div>
      )}
      {trades.length === 0 ? (
        <NoTradesYet owner={owner} addButton={addButton} />
      ) : (
        <>
          <div className="mb-4 flex min-h-7 items-center gap-2">
            <span className="text-muted">{trades.length === 1 ? '1 trade' : `${trades.length} trades`}</span>
            <span className="ml-auto">{addButton}</span>
          </div>
          <TradeTable
            trades={trades}
            stockSplits={stockSplitsOf(owner)}
            translatedTrades={translatedTrades}
            rowActions={rowActions}
          />
        </>
      )}

      {tradeModal !== null && (
        <TradeModal
          owner={owner}
          tradeBeingEdited={tradeModal.tradeBeingEdited}
          onSaved={onDataChanged}
          onClose={() => setTradeModal(null)}
        />
      )}
      {tradeToDelete !== null && (
        <ConfirmDialog
          title="Delete this trade?"
          item={tradeLineOf(
            tradeToDelete,
            translatedTradeOf(translatedTrades, tradeToDelete.id),
            symbolOf(owner),
            hasSeveralInvestors(investors) ? investorNameOf(investors, tradeToDelete.investorId) : null,
          )}
          onConfirm={() => deleteTradeAndReload(tradeToDelete)}
          onClose={() => setTradeToDelete(null)}
        />
      )}
    </div>
  );
}

/**
 * A holding's trades of one sold period, on the Closed page: read-only, since they belong to the holding — the link opens
 * its drawer in Positions, where they are corrected. `translatedTrades` are the holding's (`holding.translatedTrades`).
 * No split's line here: a period sold in full before a split wasn't touched by it, and a split at the end of its list
 * would read as if it were.
 */
export function ReadOnlyTradesPanel({
  trades,
  translatedTrades,
  symbol,
  holdingId,
}: {
  trades: Trade[];
  translatedTrades: TranslatedTrade[];
  symbol: string;
  holdingId: number;
}) {
  return (
    <div>
      <InfoBand>
        Read-only here: these are the trades of <b className="font-semibold">{symbol}</b>’s holding for this period.{' '}
        <Link
          to={`/positions/${holdingId}`}
          className="inline-flex items-center gap-[3px] rounded-sm font-medium text-accent underline underline-offset-2"
        >
          Correct them in Positions
          <ArrowRight size={12} />
        </Link>
      </InfoBand>
      <TradeTable trades={trades} stockSplits={[]} translatedTrades={translatedTrades} />
    </div>
  );
}
