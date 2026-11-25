import type { ReactNode, SyntheticEvent } from 'react';
import * as Tooltip from '@radix-ui/react-tooltip';
import { CircleHelp } from 'lucide-react';

/**
 * A row or a heading that opens on a click must not open when a "?" inside it is clicked — nor when its explanation is:
 * React passes the explanation's clicks to the row too, although it is drawn elsewhere on the page.
 */
function keepFromTheRowAround(event: SyntheticEvent) {
  event.stopPropagation();
}

/**
 * The app's one "?" (round 6): an explanation of two sentences or more, a list, or how a heading's figures are worked out,
 * set 4px after the words it explains — never at a card's far edge. One sentence about one thing is a `Hint` on the thing
 * instead. It opens at once while the pointer or the keyboard is on it, stays open while the pointer is over the
 * explanation, and closes when both leave, on Esc, or on a click. Radix's tooltip draws the explanation over the page, so
 * a card or a table around it never cuts it, and it never moves the keyboard's focus: a popover, which the Taxes page's
 * "?" was at first, gives the focus back to its button when it closes, and that reopened it (2026-11-16, the browser
 * check). `label` names it for screen readers ("What the signal colors mean").
 */
export function HelpOnHover({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Tooltip.Root delayDuration={0}>
      <Tooltip.Trigger
        type="button"
        aria-label={label}
        onClick={keepFromTheRowAround}
        onKeyDown={keepFromTheRowAround}
        className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full align-middle text-faint transition-colors hover:bg-accent-soft hover:text-accent focus-visible:bg-accent-soft focus-visible:text-accent data-[state=delayed-open]:bg-accent-soft data-[state=delayed-open]:text-accent data-[state=instant-open]:bg-accent-soft data-[state=instant-open]:text-accent"
      >
        <CircleHelp size={14} />
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content
          side="top"
          sideOffset={4}
          onClick={keepFromTheRowAround}
          // Above the drawer (z-40) and a modal (z-50), like the hints.
          className="hint-motion z-[60] w-max max-w-[300px] rounded-md border bg-surface-raised px-3 py-2 text-left text-[13px] font-normal leading-[18px] text-ink shadow-raised"
        >
          {children}
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  );
}
