import { CARD_CLASS } from '../lib/styles';
import type { Holding, HoldingSignal } from '../types/portfolio';
import { CardHeading } from './CardHeading';
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

function countsBySignal(holdings: Holding[]): Record<SignalGroup, number> {
  const counts: Record<SignalGroup, number> = { GREEN: 0, YELLOW: 0, RED: 0, none: 0 };
  holdings.forEach((holding) => (counts[holding.signal ?? 'none'] += 1));
  return counts;
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

/** How the open holdings stand: how many have each colour of dot, as a bar and a count per colour. */
export function SignalGlance({ openHoldings }: { openHoldings: Holding[] }) {
  const counts = countsBySignal(openHoldings);
  const total = openHoldings.length;

  return (
    <section aria-label="Signal at a glance" className={CARD_CLASS}>
      <CardHeading title="Signal at a glance">
        <span className="ml-auto">
          <HelpPopover label="What the signal colors mean">
            <SignalLegend />
          </HelpPopover>
        </span>
      </CardHeading>
      <div className="px-5 pb-5">
        <div className="mb-3 flex items-baseline gap-2 text-muted">
          <span className="text-figure-lg text-ink">{total}</span>
          {total === 1 ? 'open holding' : 'open holdings'}
        </div>
        {total > 0 && <SignalBar counts={counts} />}
        <ul className="divide-y">
          {SIGNAL_GROUPS.map(({ group, name, meaning }) => (
            <li key={group} className="grid grid-cols-[12px_1fr_auto] items-center gap-x-3 py-1.5">
              <SignalColorDot signal={group === 'none' ? null : group} label={name} />
              <span className="font-medium">
                {name}
                <span className="ml-2 text-caption font-normal text-muted">{meaning}</span>
              </span>
              <span className={`min-w-[2ch] text-right ${counts[group] === 0 ? 'text-faint' : 'font-semibold'}`}>
                {counts[group]}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
