import type { ReactNode } from 'react';

/** A card's title row: the title, then whatever goes beside it (`children`: a count, a "?"), the last of it on the right. */
export function CardHeading({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex min-h-[52px] items-center gap-2 px-5 pb-3 pt-4">
      <h2 className="text-section-title">{title}</h2>
      {children}
    </div>
  );
}
