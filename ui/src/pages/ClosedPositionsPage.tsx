import { useState } from 'react';
import { Plus } from 'lucide-react';
import { CARD_CLASS, SECONDARY_BUTTON_CLASS } from '../lib/styles';
import type { ClosedPosition } from '../types/portfolio';
import { ClosedDrawer } from '../components/ClosedDrawer';
import {
  ClosedPositionsTable,
  ClosedSummary,
  closedPositionKeyOf,
  newestSaleFirst,
} from '../components/ClosedPositionsTable';
import { EmptyBox } from '../components/EmptyBox';
import { ClosedPositionModal } from '../components/ManualPositionForms';
import { PageHeader } from '../components/PageHeader';

/** The row `step` away from `closedPositionKey` in the table's order, round the ends. */
function neighbourOf(rows: ClosedPosition[], closedPositionKey: string, step: number): ClosedPosition {
  const index = rows.findIndex((closedPosition) => closedPositionKeyOf(closedPosition) === closedPositionKey);
  return rows[(index + step + rows.length) % rows.length];
}

/**
 * The Closed page (/closed): every stretch of owning a stock that has had a sale, at average cost — of closed holdings
 * and open ones alike, and of the manual positions added here by hand — with its realized P&L at the top. A row opens
 * its drawer, where a manual position is corrected; every change reloads the whole portfolio.
 */
export function ClosedPositionsPage({
  closedPositions,
  onDataChanged,
}: {
  closedPositions: ClosedPosition[];
  onDataChanged: () => Promise<void>;
}) {
  const [isAdding, setIsAdding] = useState(false);
  const [openKey, setOpenKey] = useState<string | null>(null);
  const rows = newestSaleFirst(closedPositions);
  // Gone after a reload — a manual position deleted from its own drawer — the drawer closes with it.
  const openClosedPosition = rows.find((closedPosition) => closedPositionKeyOf(closedPosition) === openKey) ?? null;

  return (
    <>
      <PageHeader title="Closed">
        <button type="button" onClick={() => setIsAdding(true)} className={SECONDARY_BUTTON_CLASS}>
          <Plus size={16} />
          Add closed position
        </button>
      </PageHeader>

      {rows.length === 0 ? (
        <EmptyBox message="None yet: a holding shows up here once one of its trades is a sell, and a position sold before PortfolioBoss saw it can be added by hand." />
      ) : (
        <div className="flex flex-col gap-6">
          <ClosedSummary closedPositions={rows} />
          <section className={CARD_CLASS}>
            <ClosedPositionsTable rows={rows} selectedKey={openKey} onOpen={setOpenKey} />
          </section>
        </div>
      )}

      {openClosedPosition !== null && openKey !== null && (
        <ClosedDrawer
          key={openKey}
          closedPosition={openClosedPosition}
          closedPositions={closedPositions}
          previousLabel={neighbourOf(rows, openKey, -1).symbol}
          nextLabel={neighbourOf(rows, openKey, 1).symbol}
          onPrevious={() => setOpenKey(closedPositionKeyOf(neighbourOf(rows, openKey, -1)))}
          onNext={() => setOpenKey(closedPositionKeyOf(neighbourOf(rows, openKey, 1)))}
          onClose={() => setOpenKey(null)}
          onDataChanged={onDataChanged}
        />
      )}
      {isAdding && <ClosedPositionModal onSaved={onDataChanged} onClose={() => setIsAdding(false)} />}
    </>
  );
}
