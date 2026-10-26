import type { ReactNode } from 'react';
import { COUNT_CHIP_CLASS } from '../../lib/styles';
import type { HotStockLabel } from '../../types/recommendations';

interface ListWords {
  title: string;
  meaning: string;
  emptyText: string;
}

/** What each list is called, what it means, and what it says when it is empty (DESIGN_BRIEF.md §8.2). */
export const LIST_WORDS: Record<HotStockLabel, ListWords> = {
  HOT: {
    title: 'Hot',
    meaning: '0–15% above the 50-day average: worth a look now',
    emptyText: 'No stock is hot right now.',
  },
  TOO_HOT: {
    title: 'Too hot',
    meaning: 'more than 15% above the 50-day average: wait for a pullback',
    emptyText: 'No stock is too hot right now.',
  },
  CORRECTING: {
    title: 'Correcting',
    meaning: 'below the 50-day average: wait for it to climb back above',
    emptyText: 'No stock is correcting right now.',
  },
};

/** The band's three parts, left to right: below the 50-day average, 0–15% above it, more than 15% above. */
const BAND_ORDER: HotStockLabel[] = ['CORRECTING', 'HOT', 'TOO_HOT'];

/**
 * Where the list sits against the 50-day average, as three small grey segments with its own darker. Grey on purpose:
 * the three are lists, not tones — never green, amber and red (§8.5).
 */
function PositionBand({ list }: { list: HotStockLabel }) {
  return (
    <span
      title="Where this list sits against the 50-day average: below · 0–15% above · more than 15% above"
      className="inline-flex gap-0.5"
    >
      {BAND_ORDER.map((bandList) => (
        <span
          key={bandList}
          aria-hidden="true"
          className={`h-1.5 w-3 rounded-[1px] ${bandList === list ? 'bg-[var(--color-text-muted)]' : 'bg-line-strong'}`}
        />
      ))}
    </span>
  );
}

/**
 * The head of a list's card: its name, how many stocks it has, what it means in words, and the band; `aside` goes at
 * the right end (the Hot list's "?").
 */
export function ListHeading({ list, count, aside }: { list: HotStockLabel; count: number; aside?: ReactNode }) {
  const { title, meaning } = LIST_WORDS[list];
  return (
    <div className="flex min-h-[52px] items-center gap-2 border-b px-5 py-3">
      <h2 className="text-section-title">{title}</h2>
      <span className={COUNT_CHIP_CLASS}>{count}</span>
      <span className="text-[13px] text-muted">{meaning}</span>
      <span className="ml-auto flex items-center gap-2">
        <PositionBand list={list} />
        {aside}
      </span>
    </div>
  );
}
