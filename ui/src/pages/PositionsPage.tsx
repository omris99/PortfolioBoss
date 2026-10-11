import { useState } from 'react';
import { TriangleAlert } from 'lucide-react';
import { useNavigate, useParams } from 'react-router';
import { openHoldingsOf } from '../lib/holdings';
import { CARD_CLASS } from '../lib/styles';
import type { Holding, PortfolioSnapshot } from '../types/portfolio';
import { AnalyzeButton } from '../components/AnalyzeButton';
import { EmptyBox } from '../components/EmptyBox';
import { needsAttention } from '../components/HoldingWarnings';
import { PageHeader } from '../components/PageHeader';
import { PositionsTable } from '../components/PositionsTable';

/**
 * "2 holdings need attention (1 closed, hidden)", or `null` when none does. It counts every holding, not only the
 * shown ones: "closed without a sell" is only ever on a closed holding, which is hidden by default.
 */
function attentionSummaryOf(allHoldings: Holding[], showsClosed: boolean): string | null {
  const holdingsNeedingAttention = allHoldings.filter(needsAttention);
  if (holdingsNeedingAttention.length === 0) return null;

  const hiddenCount = showsClosed
    ? 0
    : holdingsNeedingAttention.filter((holding) => holding.status === 'CLOSED').length;
  const subjectAndVerb = holdingsNeedingAttention.length === 1 ? 'holding needs' : 'holdings need';
  const hiddenNote = hiddenCount > 0 ? ` (${hiddenCount} closed, hidden)` : '';
  return `${holdingsNeedingAttention.length} ${subjectAndVerb} attention${hiddenNote}`;
}

function AttentionSummary({ summary }: { summary: string }) {
  return (
    <span className="flex items-center gap-2 text-warn-real">
      <TriangleAlert size={14} className="shrink-0" />
      {summary}
    </span>
  );
}

function ShowClosedToggle({
  closedCount,
  showsClosed,
  onChange,
}: {
  closedCount: number;
  showsClosed: boolean;
  onChange: (showsClosed: boolean) => void;
}) {
  return (
    <label className="flex cursor-pointer items-center gap-2 text-muted">
      <input
        type="checkbox"
        checked={showsClosed}
        onChange={(event) => onChange(event.target.checked)}
        className="h-4 w-4 accent-accent"
      />
      Show closed ({closedCount})
    </label>
  );
}

/** The open row's holding id, from /positions/12; `null` at /positions, or for an address that is not a number. */
function holdingIdFromAddress(holdingIdText: string | undefined): number | null {
  if (holdingIdText === undefined) return null;
  const holdingId = Number(holdingIdText);
  return Number.isInteger(holdingId) ? holdingId : null;
}

/**
 * The Positions page (/positions): every holding IB reports, sortable, and — at /positions/12 — the row of holding 12
 * open under it, with its momentum, analysis, split by investor and trades. The open row lives in the address, so the
 * Overview and the closed positions can link straight to it, and the browser's Back closes it again.
 */
export function PositionsPage({
  snapshot,
  onDataChanged,
}: {
  snapshot: PortfolioSnapshot;
  onDataChanged: () => Promise<void>;
}) {
  const navigate = useNavigate();
  const expandedHoldingId = holdingIdFromAddress(useParams().holdingId);
  const [showClosed, setShowClosed] = useState(false);

  const allHoldings = snapshot.holdings;
  const openHoldings = openHoldingsOf(allHoldings);
  const closedCount = allHoldings.length - openHoldings.length;
  // A link to a closed holding's row shows the closed holdings, or the row it opens would be hidden.
  const isExpandedHoldingClosed = allHoldings.some(
    (holding) => holding.id === expandedHoldingId && holding.status === 'CLOSED',
  );
  const showsClosed = showClosed || isExpandedHoldingClosed;
  const visibleHoldings = showsClosed ? allHoldings : openHoldings;
  const attentionSummary = attentionSummaryOf(allHoldings, showsClosed);

  const toggleExpanded = (holdingId: number) =>
    navigate(holdingId === expandedHoldingId ? '/positions' : `/positions/${holdingId}`);

  /** Hiding the closed holdings closes the open row too, when it is one of them. */
  const handleShowClosedChange = (shows: boolean) => {
    setShowClosed(shows);
    if (!shows && isExpandedHoldingClosed) navigate('/positions');
  };

  return (
    <>
      <PageHeader
        title="Positions"
        description={attentionSummary && <AttentionSummary summary={attentionSummary} />}
      >
        {openHoldings.length > 0 && (
          <AnalyzeButton
            holdingIds={null}
            label="Analyze"
            hint={`Search the web for the analyst ratings and news of every open holding, and have Claude read them: about $0.04 and 5 Tavily credits a stock (${openHoldings.length} now)`}
            onDataChanged={onDataChanged}
          />
        )}
        <ShowClosedToggle closedCount={closedCount} showsClosed={showsClosed} onChange={handleShowClosedChange} />
      </PageHeader>

      {visibleHoldings.length === 0 ? (
        <EmptyBox message="No open positions reported." />
      ) : (
        <div className={`overflow-hidden ${CARD_CLASS}`}>
          <PositionsTable
            holdings={visibleHoldings}
            closedPositions={snapshot.closedPositions}
            expandedHoldingId={expandedHoldingId}
            onToggleExpanded={toggleExpanded}
            onDataChanged={onDataChanged}
          />
        </div>
      )}
    </>
  );
}
