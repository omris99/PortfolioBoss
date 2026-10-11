import type { ReactNode } from 'react';
import * as Popover from '@radix-ui/react-popover';
import { CircleHelp } from 'lucide-react';

/**
 * A small "?" that opens an explanation — in place of legend text that would sit on screen all the time. Radix's
 * popover brings the keyboard and focus handling: Esc closes it and returns the focus to the "?", and so does a click
 * outside. `label` names the button for screen readers ("What the signal colors mean").
 */
export function HelpPopover({
  label,
  align = 'end',
  children,
}: {
  label: string;
  align?: 'start' | 'end';
  children: ReactNode;
}) {
  return (
    <Popover.Root>
      <Popover.Trigger
        aria-label={label}
        className="inline-flex h-6 w-6 items-center justify-center rounded-full text-faint transition-colors hover:bg-accent-soft hover:text-accent data-[state=open]:bg-accent-soft data-[state=open]:text-accent"
      >
        <CircleHelp size={16} />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align={align}
          sideOffset={6}
          aria-label={label}
          // Above the drawer (z-40) and a modal (z-50): a "?" can sit inside either.
          className="z-[60] w-[300px] rounded-md border bg-surface-raised px-4 py-3 text-[13px] leading-[18px] text-ink shadow-raised"
        >
          {children}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
