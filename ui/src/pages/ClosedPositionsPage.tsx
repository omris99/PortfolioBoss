import { useState } from 'react';
import { Plus } from 'lucide-react';
import { CARD_CLASS, PRIMARY_BUTTON_CLASS } from '../lib/styles';
import type { ClosedPosition } from '../types/portfolio';
import { ClosedPositionsTable, RealizedPnlTotals } from '../components/ClosedPositionsTable';
import { EmptyBox } from '../components/EmptyBox';
import { NewManualPositionForm } from '../components/ManualPositionForms';
import { PageHeader } from '../components/PageHeader';

function AddClosedPositionButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className={PRIMARY_BUTTON_CLASS}>
      <Plus size={16} />
      Add closed position
    </button>
  );
}

/**
 * The Closed page (/closed): every stretch of owning a stock that has had a sale, at average cost — of closed holdings
 * and open ones alike, and of the manual positions added here by hand. A manual position is corrected in its row; every
 * change reloads the whole portfolio, like the trades panel.
 */
export function ClosedPositionsPage({
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
    <>
      <PageHeader
        title="Closed positions"
        description="Shares sold, at average cost: from the trades entered, and from positions added here by hand."
      >
        {hasClosedPositions && <RealizedPnlTotals closedPositions={closedPositions} />}
        {!isAdding && <AddClosedPositionButton onClick={() => setIsAdding(true)} />}
      </PageHeader>

      <div className="flex flex-col gap-6">
        {isAdding && <NewManualPositionForm onSaved={handleAdded} onCancel={() => setIsAdding(false)} />}

        {hasClosedPositions ? (
          <div className={`overflow-hidden ${CARD_CLASS}`}>
            <ClosedPositionsTable closedPositions={closedPositions} onDataChanged={onDataChanged} />
          </div>
        ) : (
          <EmptyBox message="None yet: a holding shows up here once one of its trades is a sell, and a position sold before PortfolioBoss saw it can be added by hand." />
        )}
      </div>
    </>
  );
}
