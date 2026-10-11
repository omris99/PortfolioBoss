import { useState, type ReactNode } from 'react';
import { ArrowRight, Pencil, Plus, Trash2 } from 'lucide-react';
import { Link } from 'react-router';
import { deleteTrade } from '../lib/apiClient';
import { EMPTY_VALUE, formatMoney, formatQuantity } from '../lib/format';
import {
  DELETE_ICON_BUTTON_CLASS,
  EXPLAINED_CLASS,
  ICON_BUTTON_CLASS,
  INNER_CELL_CLASS,
  INNER_HEADER_CELL_CLASS,
  INNER_TABLE_CLASS,
  INNER_TABLE_FRAME_CLASS,
  SMALL_PRIMARY_BUTTON_CLASS,
  SMALL_SECONDARY_BUTTON_CLASS,
  sideTextClass,
} from '../lib/styles';
import type { Trade } from '../types/portfolio';
import { InfoBand } from './FormFields';
import { HoldingWarningBands } from './HoldingWarnings';
import { hasSeveralInvestors, investorNameOf, useInvestors } from './InvestorsContext';
import { ConfirmDialog } from './Modal';
import { backfilledHoldingOf, symbolOf, TradeModal, type TradeOwner } from './TradeModal';

/** What the ✏️ and 🗑 of each row do. A table without them is read-only. */
interface TradeRowActions {
  onEdit: (trade: Trade) => void;
  onDelete: (trade: Trade) => void;
}

function TradeRow({
  trade,
  investorName,
  rowActions,
}: {
  trade: Trade;
  /** `null` while the account owner is the only investor: the column is then left out. */
  investorName: string | null;
  rowActions: TradeRowActions | null;
}) {
  return (
    <tr>
      <td className={INNER_CELL_CLASS}>{trade.tradeDate}</td>
      <td className={INNER_CELL_CLASS}>
        <span className={sideTextClass(trade.side === 'SELL')}>{trade.side}</span>
      </td>
      <td className={`${INNER_CELL_CLASS} text-right`}>{formatQuantity(trade.quantity)}</td>
      <td className={`${INNER_CELL_CLASS} text-right`}>
        {trade.price === null ? (
          <span title="No price entered for this trade." className={EXPLAINED_CLASS}>
            {EMPTY_VALUE}
          </span>
        ) : (
          formatMoney(trade.price)
        )}
      </td>
      <td className={`${INNER_CELL_CLASS} text-right`}>{formatMoney(trade.commission)}</td>
      <td title={trade.note ?? undefined} className={`${INNER_CELL_CLASS} max-w-[120px] truncate`}>
        {trade.note ?? <span className="text-faint">{EMPTY_VALUE}</span>}
      </td>
      {investorName !== null && <td className={INNER_CELL_CLASS}>{investorName}</td>}
      {rowActions !== null && (
        <td className={`${INNER_CELL_CLASS} w-14 text-right`}>
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
  );
}

/**
 * The trades as a compact table, by date. With `rowActions` every row can be corrected and deleted; without, it is
 * read-only. Whose each trade is shows in a column once there is more than one investor.
 */
function TradeTable({ trades, rowActions = null }: { trades: Trade[]; rowActions?: TradeRowActions | null }) {
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
          {trades.map((trade) => (
            <TradeRow
              key={trade.id}
              trade={trade}
              investorName={showsInvestor ? investorNameOf(investors, trade.investorId) : null}
              rowActions={rowActions}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}

function tradesOf(owner: TradeOwner): Trade[] {
  return owner.kind === 'holding' ? owner.holding.trades : owner.trades;
}

/** "2026-08-14 · SELL 10 AMZN at 205.00 · Me": the trade being deleted, in one line. */
function tradeLineOf(trade: Trade, symbol: string, investorName: string | null): string {
  const price = trade.price === null ? '' : ` at ${formatMoney(trade.price)}`;
  const investor = investorName === null ? '' : ` · ${investorName}`;
  return `${trade.tradeDate} · ${trade.side} ${formatQuantity(trade.quantity)} ${symbol}${price}${investor}`;
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
          <TradeTable trades={trades} rowActions={rowActions} />
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
 * its drawer in Positions, where they are corrected.
 */
export function ReadOnlyTradesPanel({
  trades,
  symbol,
  holdingId,
}: {
  trades: Trade[];
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
      <TradeTable trades={trades} />
    </div>
  );
}
