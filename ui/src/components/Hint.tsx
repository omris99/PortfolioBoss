import type { ReactNode } from 'react';
import * as Tooltip from '@radix-ui/react-tooltip';

/** How long the pointer rests on an item before its hint shows, so that a pointer passing over the page opens none. */
const HOVER_DELAY_MS = 400;

/**
 * The one way to explain an item on hover (DESIGN_BRIEF.md §11, round 6): a small bubble after the pointer has rested on
 * the item for 400 ms, at once when the keyboard reaches it, gone when either leaves or on Esc. It is Radix's tooltip, like
 * `HelpOnHover`: drawn over the page, so no card or scrolling table cuts it, and it never moves the keyboard's focus.
 * Round 6 puts it in place of the browser's own `title`, the weights' `Tip` and the Taxes page's "?", page by page.
 *
 * @param text      what the bubble says
 * @param side      above the item (the default) or below it
 * @param align     which of the item's edges the bubble lines up with
 * @param focusable false when the item is itself a button or a link, which the keyboard already reaches
 * @param children  the item
 */
export function Hint({
  text,
  side = 'top',
  align = 'center',
  focusable = true,
  children,
}: {
  text: ReactNode;
  side?: 'top' | 'bottom';
  align?: 'start' | 'center' | 'end';
  focusable?: boolean;
  children: ReactNode;
}) {
  return (
    <Tooltip.Provider delayDuration={HOVER_DELAY_MS}>
      <Tooltip.Root>
        <Tooltip.Trigger asChild>
          <span tabIndex={focusable ? 0 : undefined} className="inline-flex min-w-0 items-center rounded-sm">
            {children}
          </span>
        </Tooltip.Trigger>
        <Tooltip.Portal>
          <Tooltip.Content
            side={side}
            align={align}
            sideOffset={6}
            // Above the drawer (z-40) and a modal (z-50), like the other explanations.
            className="hint-motion z-[60] w-max max-w-[280px] rounded-md border bg-surface-raised px-2.5 py-1.5 text-[13px] font-normal leading-[18px] text-ink shadow-raised"
          >
            {text}
          </Tooltip.Content>
        </Tooltip.Portal>
      </Tooltip.Root>
    </Tooltip.Provider>
  );
}
