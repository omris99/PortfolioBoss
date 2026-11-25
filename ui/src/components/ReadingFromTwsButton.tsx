import { RefreshCw } from 'lucide-react';
import type { ReadingFromTwsState } from '../hooks/useReadingFromTws';
import { SECONDARY_BUTTON_CLASS, SMALL_SECONDARY_BUTTON_CLASS } from '../lib/styles';

/** Scan again's size, in its card, and Sync now's, in the top bar. */
type ButtonSize = 'regular' | 'small';

const BUTTON_CLASS_OF_SIZE: Record<ButtonSize, string> = {
  regular: SECONDARY_BUTTON_CLASS,
  small: SMALL_SECONDARY_BUTTON_CLASS,
};

const ICON_SIZE_OF_SIZE: Record<ButtonSize, number> = { regular: 16, small: 14 };

/**
 * A button that asks the server to read TWS — Scan again, Sync now: ↻ and `idleText`; while its reading runs, the ↻
 * turning once every 1.2 seconds (round 6's Motion; still under reduce motion) and `readingText`, disabled until it ends
 * — and while `disabled` (Sync now during the first load). What happened after is each button's own to say. `className`
 * adds to the button's own classes (Sync now's least width).
 */
export function ReadingFromTwsButton({
  readingState,
  idleText,
  readingText,
  size,
  disabled = false,
  className = '',
  title,
  onClick,
}: {
  readingState: ReadingFromTwsState;
  idleText: string;
  readingText: string;
  size: ButtonSize;
  disabled?: boolean;
  className?: string;
  title?: string;
  onClick: () => void;
}) {
  const isReading = readingState.kind === 'reading';
  const iconSize = ICON_SIZE_OF_SIZE[size];
  return (
    <button
      type="button"
      disabled={isReading || disabled}
      onClick={onClick}
      title={title}
      className={`${BUTTON_CLASS_OF_SIZE[size]} ${className}`}
    >
      <RefreshCw size={iconSize} className={isReading ? 'turning-icon' : undefined} aria-hidden="true" />
      {isReading ? readingText : idleText}
    </button>
  );
}
