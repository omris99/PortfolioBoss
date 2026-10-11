import { ChevronDown, ChevronRight } from 'lucide-react';
import { ICON_BUTTON_CLASS } from '../lib/styles';

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
      className={ICON_BUTTON_CLASS}
    >
      {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
    </button>
  );
}
