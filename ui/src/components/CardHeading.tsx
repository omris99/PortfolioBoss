import type { ReactNode } from 'react';

/**
 * A card's title row: the title with its "?" 4px after it (`help`: round 6 — never at the card's far edge), then
 * whatever goes beside them (`children`: a count), the last of it on the right.
 */
export function CardHeading({ title, help, children }: { title: string; help?: ReactNode; children?: ReactNode }) {
  return (
    <div className="flex min-h-[52px] items-center gap-2 px-5 pb-3 pt-4">
      <span className="inline-flex items-center gap-1">
        <h2 className="text-section-title">{title}</h2>
        {help}
      </span>
      {children}
    </div>
  );
}
