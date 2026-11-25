import { Hint } from './Hint';

/**
 * A text cut short with "…" where it doesn't fit — a trade's note, a sector, IB's company name —, the whole of it in its
 * hint. `className` sets its look and how wide it may grow (`max-w-[120px]`); without a width of its own it is cut where
 * the row around it ends, which must then let it shrink (a flex row).
 */
export function ShortenedText({ text, className = '' }: { text: string; className?: string }) {
  return (
    <Hint text={text} align="start">
      <span className={`min-w-0 truncate ${className}`}>{text}</span>
    </Hint>
  );
}
