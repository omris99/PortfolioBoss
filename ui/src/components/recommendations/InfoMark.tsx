import { ArrowDownRight, Link2, type LucideIcon } from 'lucide-react';

interface InfoMarkLook {
  text: string;
  icon: LucideIcon;
  hint: string;
}

const INFO_MARKS = {
  pulledBack: {
    text: 'Pulled back',
    icon: ArrowDownRight,
    hint: '5–15% below its highest close of the year: a small pullback is behind it. These come first in the Hot list.',
  },
  movesTogether: {
    text: 'Moves together',
    icon: Link2,
    hint: 'A correlation of 0.7 or more with a holding: buying it adds little spread to what you hold.',
  },
} satisfies Record<string, InfoMarkLook>;

export type InfoMarkKind = keyof typeof INFO_MARKS;

/**
 * Pulled back or Moves together (DESIGN_BRIEF.md §8.5): information, neither good nor bad — grey, never green or red.
 * Recovering is not one of these: it is a risk sign (`RecoveringTag`).
 */
export function InfoMark({ kind }: { kind: InfoMarkKind }) {
  const { text, icon: Icon, hint } = INFO_MARKS[kind];
  return (
    <span
      title={hint}
      className="inline-flex h-[18px] items-center gap-1 whitespace-nowrap rounded-sm bg-subtle pl-1 pr-[5px] text-[11px] font-medium text-muted"
    >
      <Icon size={12} aria-hidden="true" />
      {text}
    </span>
  );
}
