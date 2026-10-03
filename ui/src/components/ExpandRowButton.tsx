import { ChevronDown, ChevronRight } from 'lucide-react';

/** The chevron that opens a row's trades under it, in the positions table and the closed positions table alike. */
export function ExpandRowButton({
  isExpanded,
  onToggle,
  subject,
  expandHint,
}: {
  isExpanded: boolean;
  onToggle: () => void;
  /** What opens, for screen readers: "the trades of AAPL". */
  subject: string;
  /** Shown on hover while the row is closed. */
  expandHint: string;
}) {
  return (
    <button
      type="button"
      aria-expanded={isExpanded}
      title={isExpanded ? 'Hide trades' : expandHint}
      aria-label={`${isExpanded ? 'Hide' : 'Show'} ${subject}`}
      onClick={onToggle}
      className="rounded p-0.5 text-slate-500 transition-colors hover:bg-slate-800 hover:text-slate-200"
    >
      {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
    </button>
  );
}
