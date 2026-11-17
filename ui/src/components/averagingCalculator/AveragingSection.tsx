import type { ReactNode } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import { CARD_CLASS, COUNT_CHIP_CLASS } from '../../lib/styles';

/** How the section that folds stands: open or not, and how many of its rows buy. */
export interface SectionFolding {
  isOpen: boolean;
  plannedPurchaseCount: number;
  onToggle: () => void;
}

const HEADING_CLASS = 'flex min-h-[52px] w-full items-center gap-2 px-5 py-3 text-left';

function TitleWithCount({ title, count }: { title: string; count: number }) {
  return (
    <h2 className="flex items-center gap-2 text-section-title">
      {title}
      <span className={COUNT_CHIP_CLASS}>{count}</span>
    </h2>
  );
}

function plannedPurchasesText(plannedPurchaseCount: number): string {
  return plannedPurchaseCount === 1 ? '1 purchase planned' : `${plannedPurchaseCount} purchases planned`;
}

/**
 * The heading as a button that folds and unfolds the section. While folded, a purchase planned in it is never hidden
 * without a sign: the heading says so, and the cash card already counts it.
 */
function FoldingHeading({ title, count, folding }: { title: string; count: number; folding: SectionFolding }) {
  const { isOpen, plannedPurchaseCount, onToggle } = folding;
  const ChevronIcon = isOpen ? ChevronDown : ChevronRight;
  const shapeClass = isOpen ? 'rounded-t-lg border-b' : 'rounded-lg';
  return (
    <button
      type="button"
      aria-expanded={isOpen}
      onClick={onToggle}
      className={`${HEADING_CLASS} ${shapeClass} transition-colors hover:bg-subtle`}
    >
      <ChevronIcon size={16} aria-hidden="true" className="shrink-0 text-muted" />
      <TitleWithCount title={title} count={count} />
      {plannedPurchaseCount > 0 && (
        <span className="inline-flex h-[22px] items-center rounded-sm bg-accent-soft px-2 text-[13px] font-semibold text-accent">
          {plannedPurchasesText(plannedPurchaseCount)}
        </span>
      )}
      <span className="ml-auto text-caption text-faint">
        {isOpen ? 'A purchase here raises the average.' : 'Closest to its average first'}
      </span>
    </button>
  );
}

/**
 * A section of the Averaging calculator, in its card: its heading with the count, a quiet `lead` line, then the table
 * (`children`) — or `emptyText` when it has no holding. With `folding` the whole heading folds it.
 */
export function AveragingSection({
  title,
  count,
  lead = null,
  emptyText = null,
  folding = null,
  children,
}: {
  title: string;
  count: number;
  lead?: string | null;
  emptyText?: string | null;
  folding?: SectionFolding | null;
  children: ReactNode;
}) {
  const isShown = folding === null || folding.isOpen;
  return (
    <section aria-label={title} className={CARD_CLASS}>
      {folding === null ? (
        <div className={`${HEADING_CLASS} border-b`}>
          <TitleWithCount title={title} count={count} />
        </div>
      ) : (
        <FoldingHeading title={title} count={count} folding={folding} />
      )}
      {isShown && count === 0 && emptyText !== null && (
        <p className="px-5 py-6 text-center text-muted">{emptyText}</p>
      )}
      {isShown && count > 0 && (
        <>
          {lead !== null && <p className="border-b px-5 py-3 text-[13px] text-muted">{lead}</p>}
          {children}
        </>
      )}
    </section>
  );
}
