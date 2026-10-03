import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import { deleteManualPosition, errorMessageOf } from '../lib/apiClient';
import type { ClosedPosition } from '../types/portfolio';
import { ManualPositionDetailsForm } from './ManualPositionForms';
import { TradesPanel } from './TradesPanel';

function confirmDeletion(closedPosition: ClosedPosition): boolean {
  const tradeCount = closedPosition.trades.length;
  return window.confirm(
    `Delete the manual position ${closedPosition.symbol} and its ${tradeCount} ${tradeCount === 1 ? 'trade' : 'trades'}?`,
  );
}

function DeletePositionButton({ isDeleting, onDelete }: { isDeleting: boolean; onDelete: () => void }) {
  return (
    <button
      type="button"
      onClick={onDelete}
      disabled={isDeleting}
      className="flex items-center gap-1 rounded-md border border-rose-500/40 px-3 py-1 text-xs text-rose-300 transition-colors hover:bg-rose-500/10 disabled:opacity-50"
    >
      <Trash2 size={12} />
      {isDeleting ? 'Deleting…' : 'Delete position'}
    </button>
  );
}

/**
 * Under a manual position's row in the closed positions table: its details, a button that deletes it with its trades,
 * and its buys and sells — corrected, deleted and added like a holding's, in the same trades panel. Its last sell can
 * be corrected but not deleted (the API answers why).
 */
export function ManualPositionPanel({
  manualPositionId,
  closedPosition,
  onDataChanged,
}: {
  manualPositionId: number;
  closedPosition: ClosedPosition;
  onDataChanged: () => Promise<void>;
}) {
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);

  const handleDeletePosition = async () => {
    if (!confirmDeletion(closedPosition)) return;
    setIsDeleting(true);
    setDeleteErrorMessage(null);
    try {
      await deleteManualPosition(manualPositionId);
      await onDataChanged();   // the row, and this panel with it, goes away
    } catch (error) {
      setDeleteErrorMessage(errorMessageOf(error));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end justify-between gap-3 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300">
        <ManualPositionDetailsForm
          manualPositionId={manualPositionId}
          closedPosition={closedPosition}
          onSaved={onDataChanged}
        />
        <DeletePositionButton isDeleting={isDeleting} onDelete={() => void handleDeletePosition()} />
      </div>
      {deleteErrorMessage && <p className="text-[11px] text-rose-400">{deleteErrorMessage}</p>}

      <TradesPanel
        owner={{
          kind: 'manualPosition',
          manualPositionId,
          symbol: closedPosition.symbol,
          trades: closedPosition.trades,
        }}
        onDataChanged={onDataChanged}
      />
    </div>
  );
}
