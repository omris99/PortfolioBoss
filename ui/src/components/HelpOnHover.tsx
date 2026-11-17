import type { ReactNode, SyntheticEvent } from 'react';
import * as Tooltip from '@radix-ui/react-tooltip';
import { CircleHelp } from 'lucide-react';

/**
 * A row that opens on a click must not open when a "?" inside it is clicked — nor when its explanation is: React passes
 * the explanation's clicks to the row too, although it is drawn elsewhere on the page.
 */
function keepFromTheRowAround(event: SyntheticEvent) {
  event.stopPropagation();
}

/**
 * A small "?" whose explanation opens while the pointer is on it or the keyboard is on it, and closes when either leaves,
 * on Esc, or on a click — the Taxes page's, which has one beside many figures. Radix's tooltip draws the explanation over
 * the page, so a card or a table around it never cuts it, and it never moves the keyboard's focus: a popover, which this
 * was at first, gives the focus back to its button when it closes, and that reopened it (2026-11-16, the browser check).
 * `icon` takes the place of the "?" where something else explains itself, like the ✓ of a confirmed day. `label` names it
 * for screen readers ("When this day settled").
 */
export function HelpOnHover({
  label,
  icon,
  children,
}: {
  label: string;
  icon?: ReactNode;
  children: ReactNode;
}) {
  return (
    // A provider of its own, so that a "?" works wherever it is put; it opens at once, as the app's other explanations.
    <Tooltip.Provider delayDuration={0}>
      <Tooltip.Root>
        <Tooltip.Trigger
          type="button"
          aria-label={label}
          onClick={keepFromTheRowAround}
          onKeyDown={keepFromTheRowAround}
          className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full align-middle text-faint transition-colors hover:text-accent focus-visible:text-accent data-[state=delayed-open]:text-accent data-[state=instant-open]:text-accent"
        >
          {icon ?? <CircleHelp size={14} />}
        </Tooltip.Trigger>
        <Tooltip.Portal>
          <Tooltip.Content
            side="top"
            sideOffset={4}
            onClick={keepFromTheRowAround}
            // Above the drawer (z-40) and a modal (z-50), like the other explanations.
            className="z-[60] w-max max-w-[300px] rounded-md border bg-surface-raised px-3 py-2 text-[13px] font-normal leading-[18px] text-ink shadow-raised"
          >
            {children}
          </Tooltip.Content>
        </Tooltip.Portal>
      </Tooltip.Root>
    </Tooltip.Provider>
  );
}
