import { useState } from 'react';
import { Plus } from 'lucide-react';
import { deleteManualClosedPosition, errorMessageOf } from '../lib/apiClient';
import { formatQuantity } from '../lib/format';
import type { ClosedPosition } from '../types/portfolio';
import { ClosedPositionsTable, RealizedPnlTotals, type ManualRowActions } from './ClosedPositionsTable';
import { EmptyBox } from './EmptyBox';
import { ManualClosedPositionForm } from './ManualClosedPositionForm';

/** The form above the table: hidden, adding a row, or correcting the row entered by hand given. */
type FormState = { mode: 'hidden' } | { mode: 'adding' } | { mode: 'editing'; rowBeingEdited: ClosedPosition };

function confirmDeletion(closedPosition: ClosedPosition): boolean {
  return window.confirm(
    `Delete the closed position of ${formatQuantity(closedPosition.quantity)} ${closedPosition.symbol}, ` +
      `sold on ${closedPosition.closeDate}?`,
  );
}

/** A new key remounts the form, so it starts from the values of the row now chosen. */
function formKeyOf(formState: FormState): string {
  return formState.mode === 'editing' ? `edit-${formState.rowBeingEdited.manualClosedPositionId}` : 'new';
}

function AddClosedPositionButton({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-1 rounded-md border border-emerald-500/50 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20"
    >
      <Plus size={12} />
      Add closed position
    </button>
  );
}

/**
 * Every position bought and sold back to zero: derived from the trades entered, of closed holdings and open ones
 * alike, and the ones entered here by hand. Every change reloads the whole portfolio, like the trades panel.
 */
export function ClosedPositionsSection({
  closedPositions,
  onDataChanged,
}: {
  closedPositions: ClosedPosition[];
  onDataChanged: () => Promise<void>;
}) {
  const [formState, setFormState] = useState<FormState>({ mode: 'hidden' });
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);

  const hasClosedPositions = closedPositions.length > 0;
  const idBeingEdited = formState.mode === 'editing' ? formState.rowBeingEdited.manualClosedPositionId : null;

  const hideForm = () => setFormState({ mode: 'hidden' });

  const handleSaved = async () => {
    await onDataChanged();
    hideForm();
  };

  const handleDelete = async (closedPosition: ClosedPosition) => {
    const manualClosedPositionId = closedPosition.manualClosedPositionId;
    if (manualClosedPositionId === null || !confirmDeletion(closedPosition)) return;
    setDeletingId(manualClosedPositionId);
    setDeleteErrorMessage(null);
    try {
      await deleteManualClosedPosition(manualClosedPositionId);
      await onDataChanged();
      if (idBeingEdited === manualClosedPositionId) hideForm();
    } catch (error) {
      setDeleteErrorMessage(errorMessageOf(error));
    } finally {
      setDeletingId(null);
    }
  };

  const manualRowActions: ManualRowActions = {
    manualClosedPositionIdBeingEdited: idBeingEdited,
    manualClosedPositionIdBeingDeleted: deletingId,
    onEdit: (closedPosition) => setFormState({ mode: 'editing', rowBeingEdited: closedPosition }),
    onDelete: (closedPosition) => void handleDelete(closedPosition),
  };

  return (
    <section className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-semibold text-slate-100">Closed positions</div>
          <div className="mt-1 text-xs text-slate-400">
            Bought and sold back to zero: from the trades entered, or added here by hand.
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-4">
          {hasClosedPositions && <RealizedPnlTotals closedPositions={closedPositions} />}
          <AddClosedPositionButton onClick={() => setFormState({ mode: 'adding' })} />
        </div>
      </div>

      {formState.mode !== 'hidden' && (
        <div className="mt-4">
          <ManualClosedPositionForm
            key={formKeyOf(formState)}
            rowBeingEdited={formState.mode === 'editing' ? formState.rowBeingEdited : null}
            onSaved={handleSaved}
            onCancel={hideForm}
          />
        </div>
      )}
      {deleteErrorMessage && <p className="mt-2 text-[11px] text-rose-400">{deleteErrorMessage}</p>}

      <div className="mt-4">
        {hasClosedPositions ? (
          <ClosedPositionsTable closedPositions={closedPositions} manualRowActions={manualRowActions} />
        ) : (
          <EmptyBox message="None yet: a holding shows up here once its trades include a sell back to zero, and a position sold before PortfolioBoss saw it can be added by hand." />
        )}
      </div>
    </section>
  );
}
