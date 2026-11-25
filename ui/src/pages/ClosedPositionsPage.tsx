import { useState } from 'react';
import { Plus } from 'lucide-react';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { CARD_CLASS, SECONDARY_BUTTON_CLASS } from '../lib/styles';
import type { ClosedPosition, Holding, TranslatedTrade } from '../types/portfolio';
import { ClosedDrawer, closedDrawerLabelOf } from '../components/ClosedDrawer';
import { DrawerShell } from '../components/DrawerShell';
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

/** The trades a split translated in the holding a closed position is of; none for a manual position. */
function translatedTradesOfItsHolding(closedPosition: ClosedPosition, holdings: Holding[]): TranslatedTrade[] {
  const itsHolding = holdings.find((holding) => holding.id === closedPosition.holdingId);
  return itsHolding?.translatedTrades ?? [];
}

/**
 * The Closed page (/closed): every stretch of owning a stock that has had a sale, at average cost — of closed holdings
 * and open ones alike, and of the manual positions added here by hand — with its realized P&L at the top. A row opens
 * its drawer, where a manual position is corrected; every change reloads the whole portfolio. `holdings` are for a
 * holding's trades a split translated, shown in its closed position's trades.
 */
export function ClosedPositionsPage({
  closedPositions,
  holdings,
  onDataChanged,
}: {
  closedPositions: ClosedPosition[];
  holdings: Holding[];
  onDataChanged: () => Promise<void>;
}) {
  const [isAdding, setIsAdding] = useState(false);
  const [openKey, setOpenKey] = useState<string | null>(null);
  const rows = newestSaleFirst(closedPositions);
  // Gone after a reload — a manual position deleted from its own drawer — the drawer closes with it.
  const openClosedPosition = rows.find((closedPosition) => closedPositionKeyOf(closedPosition) === openKey) ?? null;
  useDocumentTitle('Closed', openClosedPosition?.symbol ?? null);

  return (
    <>
      <PageHeader title="Closed" description="Positions already sold, and what each one earned.">
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

      {/* One shell while a drawer is open, a new drawer in it per position: ‹ › fade the next one in. */}
      {openClosedPosition !== null && openKey !== null && (
        <DrawerShell ariaLabel={closedDrawerLabelOf(openClosedPosition)} onClose={() => setOpenKey(null)}>
          <ClosedDrawer
            key={openKey}
            closedPosition={openClosedPosition}
            closedPositions={closedPositions}
            translatedTradesOfItsHolding={translatedTradesOfItsHolding(openClosedPosition, holdings)}
            navigation={{
              previousLabel: neighbourOf(rows, openKey, -1).symbol,
              nextLabel: neighbourOf(rows, openKey, 1).symbol,
              onPrevious: () => setOpenKey(closedPositionKeyOf(neighbourOf(rows, openKey, -1))),
              onNext: () => setOpenKey(closedPositionKeyOf(neighbourOf(rows, openKey, 1))),
            }}
            onDataChanged={onDataChanged}
          />
        </DrawerShell>
      )}
      {isAdding && <ClosedPositionModal onSaved={onDataChanged} onClose={() => setIsAdding(false)} />}
    </>
  );
}
