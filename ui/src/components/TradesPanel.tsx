import { useState } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import { deleteTrade, errorMessageOf } from '../lib/apiClient';
import { EMPTY_VALUE, formatMoney, formatQuantity } from '../lib/format';
import type { Holding, Trade, TradeSide } from '../types/portfolio';
import { TradeForm } from './TradeForm';

function TradeSideBadge({ side }: { side: TradeSide }) {
  const colorClass =
    side === 'BUY' ? 'bg-emerald-500/15 text-emerald-300' : 'bg-rose-500/15 text-rose-300';
  return <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${colorClass}`}>{side}</span>;
}

function TradeRow({
  trade,
  isBeingEdited,
  isDeleting,
  onEdit,
  onDelete,
}: {
  trade: Trade;
  isBeingEdited: boolean;
  isDeleting: boolean;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <tr className={`border-b border-slate-800/60 last:border-b-0 ${isBeingEdited ? 'bg-emerald-500/5' : ''}`}>
      <td className="px-2 py-1 font-mono">{trade.tradeDate}</td>
      <td className="px-2 py-1">
        <TradeSideBadge side={trade.side} />
      </td>
      <td className="px-2 py-1 text-right font-mono">{formatQuantity(trade.quantity)}</td>
      <td className="px-2 py-1 text-right font-mono">{formatMoney(trade.price)}</td>
      <td className="px-2 py-1 text-slate-400">{trade.note ?? EMPTY_VALUE}</td>
      <td className="px-2 py-1 text-right">
        <div className="inline-flex gap-1">
          <button
            type="button"
            title="Edit this trade"
            onClick={onEdit}
            className="rounded p-1 text-slate-400 transition-colors hover:bg-slate-800 hover:text-slate-100"
          >
            <Pencil size={12} />
          </button>
          <button
            type="button"
            title="Delete this trade"
            onClick={onDelete}
            disabled={isDeleting}
            className="rounded p-1 text-slate-400 transition-colors hover:bg-rose-500/10 hover:text-rose-300 disabled:opacity-50"
          >
            <Trash2 size={12} />
          </button>
        </div>
      </td>
    </tr>
  );
}

function confirmDeletion(trade: Trade): boolean {
  return window.confirm(`Delete the ${trade.side} of ${formatQuantity(trade.quantity)} on ${trade.tradeDate}?`);
}

/**
 * The trades entered for one holding, and the form under them. The form adds a trade, or corrects the one whose
 * pencil was clicked. Every change reloads the whole portfolio, since the dates and holding period in the row
 * above are derived from these trades on the server.
 */
export function TradesPanel({ holding, onDataChanged }: { holding: Holding; onDataChanged: () => Promise<void> }) {
  const [tradeBeingEdited, setTradeBeingEdited] = useState<Trade | null>(null);
  // A new key remounts the form, so it starts again from fresh values after each save.
  const [formGeneration, setFormGeneration] = useState(0);
  const [deletingTradeId, setDeletingTradeId] = useState<number | null>(null);
  const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);

  const resetForm = () => {
    setTradeBeingEdited(null);
    setFormGeneration((generation) => generation + 1);
  };

  const handleSaved = async () => {
    await onDataChanged();
    resetForm();
  };

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

  const formKey = tradeBeingEdited === null ? `new-${formGeneration}` : `edit-${tradeBeingEdited.id}`;

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300">
      {holding.trades.length === 0 ? (
        <p className="text-slate-500">No trades entered yet for {holding.symbol}.</p>
      ) : (
        <table className="w-full border-collapse">
          <thead>
            <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-500">
              <th className="px-2 py-1 text-left font-medium">Date</th>
              <th className="px-2 py-1 text-left font-medium">Side</th>
              <th className="px-2 py-1 text-right font-medium">Qty</th>
              <th className="px-2 py-1 text-right font-medium">Price</th>
              <th className="px-2 py-1 text-left font-medium">Note</th>
              <th className="px-2 py-1">
                <span className="sr-only">Actions</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {holding.trades.map((trade) => (
              <TradeRow
                key={trade.id}
                trade={trade}
                isBeingEdited={tradeBeingEdited?.id === trade.id}
                isDeleting={deletingTradeId === trade.id}
                onEdit={() => setTradeBeingEdited(trade)}
                onDelete={() => void handleDelete(trade)}
              />
            ))}
          </tbody>
        </table>
      )}
      {deleteErrorMessage && <p className="text-[11px] text-rose-400">{deleteErrorMessage}</p>}

      <TradeForm
        key={formKey}
        holding={holding}
        tradeBeingEdited={tradeBeingEdited}
        onSaved={handleSaved}
        onCancelEditing={resetForm}
      />
    </div>
  );
}
