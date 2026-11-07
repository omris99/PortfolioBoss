import { Info } from 'lucide-react';
import type { LeftOutHolding } from './averagingPlan';

/**
 * Under the sections, only when the page leaves a holding of the tab out: each one with the Weight tab's own sentence
 * for why — "0700 · This holding is in HKD: the calculator works in USD only for now." —, so each reason is written in
 * one place.
 */
export function NotOnThisPage({ leftOutHoldings }: { leftOutHoldings: LeftOutHolding[] }) {
  if (leftOutHoldings.length === 0) return null;
  return (
    <div className="flex items-start gap-1.5 text-[13px] text-muted">
      <Info size={14} aria-hidden="true" className="mt-[3px] shrink-0" />
      <div>
        Not on this page:
        <ul>
          {leftOutHoldings.map((leftOutHolding) => (
            <li key={leftOutHolding.holding.id}>
              <b className="font-semibold text-ink">{leftOutHolding.holding.symbol}</b> · {leftOutHolding.reason}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
