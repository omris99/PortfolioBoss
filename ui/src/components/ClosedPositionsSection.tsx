import { useState } from 'react';
import { Plus } from 'lucide-react';
import type { ClosedPosition } from '../types/portfolio';
import { ClosedPositionsTable, RealizedPnlTotals } from './ClosedPositionsTable';
import { EmptyBox } from './EmptyBox';
import { NewManualPositionForm } from './ManualPositionForms';

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
 * Every stretch of owning a stock that has had a sale, at average cost: of closed holdings and open ones alike, and of
 * the manual positions added here by hand. A manual position is corrected in its row; every change reloads the whole
 * portfolio, like the trades panel.
 */
export function ClosedPositionsSection({
  closedPositions,
  onDataChanged,
}: {
  closedPositions: ClosedPosition[];
  onDataChanged: () => Promise<void>;
}) {
  const [isAdding, setIsAdding] = useState(false);
  const hasClosedPositions = closedPositions.length > 0;

  const handleAdded = async () => {
    await onDataChanged();
    setIsAdding(false);
  };

  return (
    <section className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-semibold text-slate-100">Closed positions</div>
          <div className="mt-1 text-xs text-slate-400">
            Shares sold, at average cost: from the trades entered, and from positions added here by hand.
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-4">
          {hasClosedPositions && <RealizedPnlTotals closedPositions={closedPositions} />}
          <AddClosedPositionButton onClick={() => setIsAdding(true)} />
        </div>
      </div>

      {isAdding && (
        <div className="mt-4">
          <NewManualPositionForm onSaved={handleAdded} onCancel={() => setIsAdding(false)} />
        </div>
      )}

      <div className="mt-4">
        {hasClosedPositions ? (
          <ClosedPositionsTable closedPositions={closedPositions} onDataChanged={onDataChanged} />
        ) : (
          <EmptyBox message="None yet: a holding shows up here once one of its trades is a sell, and a position sold before PortfolioBoss saw it can be added by hand." />
        )}
      </div>
    </section>
  );
}
