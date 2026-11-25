import type { ReactNode } from 'react';
import { CARD_CLASS } from '../lib/styles';
import type { Holding, HoldingSignal } from '../types/portfolio';
import { CardHeading } from './CardHeading';
import { SkeletonBar } from './firstLoad/SkeletonBar';
import { HelpPopover } from './HelpPopover';
import { SignalColorDot, SignalLegend } from './Signal';

/** A holding's dot, or 'none' while it has none: not analyzed yet, or fewer than two of its three parts known. */
type SignalGroup = HoldingSignal | 'none';

const SIGNAL_GROUPS: { group: SignalGroup; name: string; meaning: string }[] = [
  { group: 'GREEN', name: 'Green', meaning: 'no warning sign' },
  { group: 'YELLOW', name: 'Yellow', meaning: 'one warning sign' },
  { group: 'RED', name: 'Red', meaning: 'two or more' },
  { group: 'none', name: 'No signal', meaning: 'not analyzed, or too little known' },
];

const BAR_SEGMENT_CLASSES: Record<SignalGroup, string> = {
  GREEN: 'bg-signal-green',
  YELLOW: 'bg-signal-yellow',
  RED: 'bg-signal-red',
  none: 'bg-unknown-soft ring-1 ring-inset ring-signal-none',
};

const CARD_TITLE = 'Signal at a glance';

function countsBySignal(holdings: Holding[]): Record<SignalGroup, number> {
  const counts: Record<SignalGroup, number> = { GREEN: 0, YELLOW: 0, RED: 0, none: 0 };
  holdings.forEach((holding) => (counts[holding.signal ?? 'none'] += 1));
  return counts;
}

/** The card's title with its "?", which explains the colours. */
function SignalGlanceHeading() {
  return (
    <CardHeading title={CARD_TITLE}>
      <span className="ml-auto">
        <HelpPopover label="What the signal colors mean">
          <SignalLegend />
        </HelpPopover>
      </span>
    </CardHeading>
  );
}

/** The colours side by side, each as long as its share of the holdings; a colour no holding has is left out. */
function SignalBar({ counts }: { counts: Record<SignalGroup, number> }) {
  return (
    <div className="mb-4 flex h-2.5 gap-0.5" aria-hidden="true">
      {SIGNAL_GROUPS.filter(({ group }) => counts[group] > 0).map(({ group }) => (
        <span
          key={group}
          className={`h-full first:rounded-l-full last:rounded-r-full ${BAR_SEGMENT_CLASSES[group]}`}
          style={{ flexGrow: counts[group], flexBasis: 0 }}
        />
      ))}
    </div>
  );
}

/** A row per colour — its dot, its name and what it means — with `countOf(group)` at the right. */
function SignalGroupList({ countOf }: { countOf: (group: SignalGroup) => ReactNode }) {
  return (
    <ul className="divide-y">
      {SIGNAL_GROUPS.map(({ group, name, meaning }) => (
        <li key={group} className="grid grid-cols-[12px_1fr_auto] items-center gap-x-3 py-1.5">
          <SignalColorDot signal={group === 'none' ? null : group} label={name} />
          <span className="font-medium">
            {name}
            <span className="ml-2 text-caption font-normal text-muted">{meaning}</span>
          </span>
          {countOf(group)}
        </li>
      ))}
    </ul>
  );
}

/** How the open holdings stand: how many have each colour of dot, as a bar and a count per colour. */
export function SignalGlance({ openHoldings }: { openHoldings: Holding[] }) {
  const counts = countsBySignal(openHoldings);
  const total = openHoldings.length;

  return (
    <section aria-label={CARD_TITLE} className={CARD_CLASS}>
      <SignalGlanceHeading />
      <div className="px-5 pb-5">
        <div className="mb-3 flex items-baseline gap-2 text-muted">
          <span className="text-figure-lg text-ink">{total}</span>
          {total === 1 ? 'open holding' : 'open holdings'}
        </div>
        {total > 0 && <SignalBar counts={counts} />}
        <SignalGroupList
          countOf={(group) => (
            <span className={`min-w-[2ch] text-right ${counts[group] === 0 ? 'text-faint' : 'font-semibold'}`}>
              {counts[group]}
            </span>
          )}
        />
      </div>
    </section>
  );
}

/**
 * The same card during the first load: its title, its "?" and the four colours with their meanings — words known before
 * the data —, a grey bar for the total and for each count, and the colours' bar grey.
 */
export function SignalGlanceSkeleton() {
  return (
    <section aria-label={CARD_TITLE} aria-busy="true" className={CARD_CLASS}>
      <SignalGlanceHeading />
      <div className="px-5 pb-5">
        <div className="mb-3 flex items-baseline gap-2 text-muted">
          <span className="text-figure-lg">
            <SkeletonBar width={32} height={24} />
          </span>
          open holdings
        </div>
        <span className="mb-4 block h-2.5 rounded-full bg-subtle" aria-hidden="true" />
        <SignalGroupList countOf={() => <SkeletonBar width={16} height={12} />} />
      </div>
    </section>
  );
}
