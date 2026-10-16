import type { ReactNode } from 'react';
import { formatPercent } from '../../lib/format';
import { CARD_CLASS } from '../../lib/styles';
import { CardHeading } from '../CardHeading';
import type { Weight } from './portfolioWeights';

/** The money in stocks and the cash keep one color each wherever a graph shows them side by side. */
export const STOCKS_COLOR_CLASS = 'bg-accent';
export const CASH_COLOR_CLASS = 'bg-accent-soft ring-1 ring-inset ring-accent';

/** The frame both weight graphs share: a card, its title, and what its percentages are of (`caption`). */
export function WeightsCard({ title, caption, children }: { title: string; caption: string; children: ReactNode }) {
  return (
    <section aria-label={title} className={`flex flex-col ${CARD_CLASS}`}>
      <CardHeading title={title}>
        <span className="ml-auto text-caption text-muted">{caption}</span>
      </CardHeading>
      {children}
    </section>
  );
}

/** "1 sector", "3 holdings". */
export function countText(count: number, noun: string): string {
  return `${count} ${count === 1 ? noun : `${noun}s`}`;
}

/**
 * "21.3% of the whole account" — `wholeName` is the tab's: "all of Shay's money" on Shay's — or `null` while its value is
 * unknown.
 */
export function accountShareText(weight: Weight, wholeName: string): string | null {
  if (weight.percentOfAccount === null) return null;
  return `${formatPercent(weight.percentOfAccount)} of ${wholeName}`;
}

/** "26.9% of the money in stocks" — `stocksName` is the tab's: "Shay's money in stocks" on Shay's. */
export function stocksShareText(weight: Weight, stocksName: string): string {
  return `${formatPercent(weight.percentOfStocks)} of ${stocksName}`;
}

/** What hovering a row adds to it, from the parts that are known: "3 holdings · 21.3% of the whole account". */
export function hintOf(parts: (string | null)[]): string {
  return parts.filter((part): part is string => part !== null).join(' · ');
}
