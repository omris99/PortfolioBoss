import { ChevronRight, CircleCheckBig } from 'lucide-react';
import { Link } from 'react-router';
import { CARD_CLASS, COUNT_CHIP_CLASS, TAG_CLASS } from '../lib/styles';
import type { Holding } from '../types/portfolio';
import { CardHeading } from './CardHeading';
import { firstRealWarningOf, hasOnlyMinorWarnings } from './HoldingWarnings';
import { SignalDot, warningSignsOf } from './Signal';
import { WarnMark } from './WarnMark';

/** Why a holding is on the list, the most pressing first: a real warning, a RED dot, no analysis yet. */
type AttentionReason =
  | { kind: 'warning'; message: string }
  | { kind: 'red'; warningSigns: string[] }
  | { kind: 'notAnalyzed' };

interface AttentionItem {
  holding: Holding;
  reason: AttentionReason;
}

const REASON_ORDER: Record<AttentionReason['kind'], number> = { warning: 0, red: 1, notAnalyzed: 2 };

/**
 * Its first reason, or `null`: a real warning counts on any holding — "closed without a sell" is only ever on a closed
 * one — while a RED dot and a missing analysis count on open holdings only, the ones the analysis runs on.
 */
function reasonOf(holding: Holding): AttentionReason | null {
  const realWarning = firstRealWarningOf(holding);
  if (realWarning !== null) return { kind: 'warning', message: realWarning.message };
  if (holding.status !== 'OPEN') return null;
  if (holding.signal === 'RED') {
    return { kind: 'red', warningSigns: warningSignsOf(holding).map((warningSign) => warningSign.toLowerCase()) };
  }
  if (holding.analysis === null) return { kind: 'notAnalyzed' };
  return null;
}

/** Every holding with a reason, each once, by reason and then by symbol. */
function attentionItemsOf(holdings: Holding[]): AttentionItem[] {
  return holdings
    .flatMap((holding) => {
      const reason = reasonOf(holding);
      return reason === null ? [] : [{ holding, reason }];
    })
    .sort(
      (first, second) =>
        REASON_ORDER[first.reason.kind] - REASON_ORDER[second.reason.kind] ||
        first.holding.symbol.localeCompare(second.holding.symbol),
    );
}

function ReasonText({ reason }: { reason: AttentionReason }) {
  switch (reason.kind) {
    case 'warning':
      return (
        <span className="flex min-w-0 items-start gap-2 text-warn-real">
          <span className="mt-0.5 flex">
            <WarnMark level="real" />
          </span>
          <span className="min-w-0">{reason.message}</span>
        </span>
      );
    case 'red':
      return (
        <span className="min-w-0 text-muted">
          <b className="font-semibold text-negative">Signal RED</b>
          {reason.warningSigns.length > 0 && `: ${reason.warningSigns.join(', ')}`}
        </span>
      );
    case 'notAnalyzed':
      return <span className="text-unknown">Not analyzed yet</span>;
  }
}

/** One row, all of it a link to the holding's row in Positions. */
function AttentionRow({ item }: { item: AttentionItem }) {
  const { holding, reason } = item;
  return (
    <li>
      <Link
        to={`/positions/${holding.id}`}
        className="group grid grid-cols-[12px_88px_1fr_16px] items-center gap-x-3 border-t px-5 py-3 transition-colors hover:bg-subtle"
      >
        <SignalDot holding={holding} />
        <span className="flex items-center gap-2 font-semibold">
          {holding.symbol}
          {holding.status === 'CLOSED' && <span className={TAG_CLASS}>closed</span>}
        </span>
        <ReasonText reason={reason} />
        <ChevronRight size={16} className="text-faint group-hover:text-ink" />
      </Link>
    </li>
  );
}

/** "3 holdings have no trades entered yet": the minor warnings are counted at the bottom, never listed. */
function MinorWarningsStrip({ count }: { count: number }) {
  if (count === 0) return null;
  const text = count === 1 ? '1 holding has no trades entered yet' : `${count} holdings have no trades entered yet`;
  return (
    <div className="mt-auto flex items-center gap-2 rounded-b-lg border-t bg-warn-minor-soft px-5 py-3 text-ink">
      <WarnMark level="minor" />
      <span>{text}</span>
      <Link to="/positions" className="ml-auto font-medium underline underline-offset-2">
        Positions
      </Link>
    </div>
  );
}

function NothingNeedsAttention() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2 border-t px-5 py-8 text-center text-muted">
      <CircleCheckBig size={20} className="text-positive" />
      Nothing needs your attention.
    </div>
  );
}

/**
 * The Overview's short list of holdings to look at, each opening its row in Positions. `holdings` are all of them, open
 * and closed.
 */
export function NeedsAttention({ holdings }: { holdings: Holding[] }) {
  const items = attentionItemsOf(holdings);
  const minorWarningCount = holdings.filter(
    (holding) => holding.status === 'OPEN' && hasOnlyMinorWarnings(holding),
  ).length;

  return (
    <section aria-label="Needs attention" className={`flex flex-col ${CARD_CLASS}`}>
      <CardHeading title="Needs attention">
        {items.length > 0 && (
          <span className={COUNT_CHIP_CLASS}>{items.length}</span>
        )}
      </CardHeading>
      {items.length === 0 ? (
        <NothingNeedsAttention />
      ) : (
        <ul>
          {items.map((item) => (
            <AttentionRow key={item.holding.id} item={item} />
          ))}
        </ul>
      )}
      <MinorWarningsStrip count={minorWarningCount} />
    </section>
  );
}
