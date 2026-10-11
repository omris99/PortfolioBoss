import { useState } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import { deleteTrade, errorMessageOf } from '../lib/apiClient';
import { EMPTY_VALUE, formatMoney, formatQuantity } from '../lib/format';
import {
  CHIP_CLASS,
  DELETE_ICON_BUTTON_CLASS,
  ERROR_TEXT_CLASS,
  ICON_BUTTON_CLASS,
  PANEL_CLASS,
  TABLE_CLASS,
  TABLE_HEAD_ROW_CLASS,
  TABLE_ROW_CLASS,
} from '../lib/styles';
import type { Trade, TradeSide } from '../types/portfolio';
import { HoldingWarningList } from './HoldingWarnings';
import { hasSeveralInvestors, investorNameOf, useInvestors } from './InvestorsContext';
import { TradeForm, type TradeOwner } from './TradeForm';

/** Grey for both: in the design only a profit or a loss is green or red. */
function TradeSideBadge({ side }: { side: TradeSide }) {
  return <span className={`${CHIP_CLASS} bg-subtle text-ink`}>{side}</span>;
}

/** What the ✏️ and 🗑 of each row do. A list without them is read-only. */
interface TradeRowActions {
  tradeIdBeingEdited: number | null;
  deletingTradeId: number | null;
  onEdit: (trade: Trade) => void;
  onDelete: (trade: Trade) => void;
}

function TradeRowButtons({ trade, rowActions }: { trade: Trade; rowActions: TradeRowActions }) {
  return (
    <div className="inline-flex gap-1">
      <button
        type="button"
        title="Edit this trade"
        onClick={() => rowActions.onEdit(trade)}
        className={ICON_BUTTON_CLASS}
      >
        <Pencil size={14} />
      </button>
      <button
        type="button"
        title="Delete this trade"
        onClick={() => rowActions.onDelete(trade)}
        disabled={rowActions.deletingTradeId === trade.id}
        className={DELETE_ICON_BUTTON_CLASS}
      >
        <Trash2 size={14} />
      </button>
    </div>
  );
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
  const isBeingEdited = rowActions?.tradeIdBeingEdited === trade.id;
  return (
    <tr className={`${TABLE_ROW_CLASS} ${isBeingEdited ? 'bg-accent-soft' : ''}`}>
      {investorName !== null && <td className="px-3 py-1.5">{investorName}</td>}
      <td className="px-3 py-1.5">{trade.tradeDate}</td>
      <td className="px-3 py-1.5">
        <TradeSideBadge side={trade.side} />
      </td>
      <td className="px-3 py-1.5 text-right">{formatQuantity(trade.quantity)}</td>
      <td className="px-3 py-1.5 text-right">{formatMoney(trade.price)}</td>
      <td className="px-3 py-1.5 text-right">{formatMoney(trade.commission)}</td>
      <td className="px-3 py-1.5 text-muted">{trade.note ?? EMPTY_VALUE}</td>
      {rowActions !== null && (
        <td className="px-3 py-1 text-right">
          <TradeRowButtons trade={trade} rowActions={rowActions} />
        </td>
      )}
    </tr>
  );
}

/**
 * The trades as a table, by date. With `rowActions` every row can be corrected and deleted; without, it is read-only.
 * Whose each trade is shows in a first column once there is more than one investor.
 */
export function TradeList({ trades, rowActions = null }: { trades: Trade[]; rowActions?: TradeRowActions | null }) {
  const investors = useInvestors();
  const showsInvestor = hasSeveralInvestors(investors);
  return (
    <table className={TABLE_CLASS}>
      <thead>
        <tr className={TABLE_HEAD_ROW_CLASS}>
          {showsInvestor && <th className="px-3 py-1.5 text-left font-medium">Investor</th>}
          <th className="px-3 py-1.5 text-left font-medium">Date</th>
          <th className="px-3 py-1.5 text-left font-medium">Side</th>
          <th className="px-3 py-1.5 text-right font-medium">Qty</th>
          <th className="px-3 py-1.5 text-right font-medium">Price</th>
          <th className="px-3 py-1.5 text-right font-medium">Commission</th>
          <th className="px-3 py-1.5 text-left font-medium">Note</th>
          {rowActions !== null && (
            <th className="px-3 py-1.5">
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
  );
}

function tradesOf(owner: TradeOwner): Trade[] {
  return owner.kind === 'holding' ? owner.holding.trades : owner.trades;
}

function symbolOf(owner: TradeOwner): string {
  return owner.kind === 'holding' ? owner.holding.symbol : owner.symbol;
}

function confirmDeletion(trade: Trade): boolean {
  return window.confirm(`Delete the ${trade.side} of ${formatQuantity(trade.quantity)} on ${trade.tradeDate}?`);
}

/**
 * The trades entered for one owner — a holding, or a manual position — and the form under them. The form adds a
 * trade, or corrects the one whose pencil was clicked. Every change reloads the whole portfolio, since the dates,
 * holding period and closed positions are derived from these trades on the server.
 */
export function TradesPanel({ owner, onDataChanged }: { owner: TradeOwner; onDataChanged: () => Promise<void> }) {
  const [tradeBeingEdited, setTradeBeingEdited] = useState<Trade | null>(null);
  // A new key remounts the form, so it starts again from fresh values after each save.
  const [formGeneration, setFormGeneration] = useState(0);
  const [deletingTradeId, setDeletingTradeId] = useState<number | null>(null);
  const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);

  const trades = tradesOf(owner);

  const resetForm = () => {
    setTradeBeingEdited(null);
    setFormGeneration((generation) => generation + 1);
  };

  const handleSaved = async () => {
    await onDataChanged();
    resetForm();
  };

  /** A manual position's last sell is refused (409), and its reason shown here. */
  const handleDelete = async (trade: Trade) => {
    if (!confirmDeletion(trade)) return;
    setDeletingTradeId(trade.id);
    setDeleteErrorMessage(null);
    try {
      await deleteTrade(trade.id);
      await onDataChanged();
      if (tradeBeingEdited?.id === trade.id) resetForm();
    } catch (error) {
      setDeleteErrorMessage(errorMessageOf(error));
    } finally {
      setDeletingTradeId(null);
    }
  };

  const rowActions: TradeRowActions = {
    tradeIdBeingEdited: tradeBeingEdited?.id ?? null,
    deletingTradeId,
    onEdit: setTradeBeingEdited,
    onDelete: (trade) => void handleDelete(trade),
  };
  const formKey = tradeBeingEdited === null ? `new-${formGeneration}` : `edit-${tradeBeingEdited.id}`;

  return (
    <div className={PANEL_CLASS}>
      {owner.kind === 'holding' && <HoldingWarningList warnings={owner.holding.warnings} />}
      {trades.length === 0 ? (
        <p className="text-muted">No trades entered yet for {symbolOf(owner)}.</p>
      ) : (
        <TradeList trades={trades} rowActions={rowActions} />
      )}
      {deleteErrorMessage && <p className={ERROR_TEXT_CLASS}>{deleteErrorMessage}</p>}

      <TradeForm
        key={formKey}
        owner={owner}
        tradeBeingEdited={tradeBeingEdited}
        onSaved={handleSaved}
        onCancelEditing={resetForm}
      />
    </div>
  );
}
