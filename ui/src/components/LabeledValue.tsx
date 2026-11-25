import type { ReactNode } from 'react';

/**
 * A "label value" pair in a line — "Signal Momentum weak · Analysts down", "Company risks Expensive, P/E 180" —, the label
 * read first: bold, in the text's color, never as grey as its value (round 6, DESIGN_BRIEF.md §11.6, rule 6). `stacked`
 * puts the label over its value, for slots on a grid that line up from row to row.
 */
export function LabeledValue({
  label,
  stacked = false,
  children,
}: {
  label: string;
  stacked?: boolean;
  children: ReactNode;
}) {
  if (stacked) {
    return (
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-caption font-semibold text-ink">{label}</span>
        <span className="min-w-0">{children}</span>
      </span>
    );
  }
  return (
    <span className="inline-flex min-w-0 items-baseline gap-1.5">
      <span className="shrink-0 font-semibold text-ink">{label}</span>
      <span className="min-w-0">{children}</span>
    </span>
  );
}
