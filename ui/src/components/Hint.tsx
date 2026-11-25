import { createContext, useContext, type ReactNode, type SyntheticEvent } from 'react';
import * as Tooltip from '@radix-ui/react-tooltip';

/** How long the pointer rests on an item before its hint shows, so that a pointer passing over the page opens none. */
const HOVER_DELAY_MS = 400;

/** Above the item, or below it — where the item sits at the top of the screen (the top bar, a card that sticks there). */
export type HintPlacement = 'above' | 'below';

const RADIX_SIDE_OF_PLACEMENT: Record<HintPlacement, 'top' | 'bottom'> = { above: 'top', below: 'bottom' };

/**
 * A term in running text whose explanation is in its hint: a dotted underline (round 6 — a figure in a table and an icon
 * don't get one).
 */
const TERM_CLASS = 'cursor-help rounded-sm underline decoration-line-strong decoration-dotted underline-offset-[3px]';

/**
 * Any other item. A disabled button inside lets the pointer through to the hint around it: the browser gives a disabled
 * button no pointer events, and its hint is often the reason it is disabled ("Another analysis is running").
 */
const ITEM_CLASS = 'inline-flex min-w-0 items-center rounded-sm [&_button:disabled]:pointer-events-none';

/**
 * Every hint and every "?" of the app share it, once around the whole app: each waits 400 ms of hover (a "?" opens at
 * once, its own choice), and never at once because another one has just closed — moving along a row of figures would
 * otherwise open a bubble over each of them.
 */
export function HintsProvider({ children }: { children: ReactNode }) {
  return (
    <Tooltip.Provider delayDuration={HOVER_DELAY_MS} skipDelayDuration={0}>
      {children}
    </Tooltip.Provider>
  );
}

/** True inside a table row the keyboard reaches as a whole; false everywhere else. */
const InsideARowReachedAsAWhole = createContext(false);

/**
 * Around a table row the keyboard reaches as a whole, one Tab stop that Enter opens (Positions, Closed, a list of
 * Recommendations, a day of the Taxes page): the hints inside it aren't Tab stops of their own (the user's choice), or a
 * table of ten rows would take some sixty Tabs. The same figures keep their stop in a drawer or a card.
 */
export function RowReachedAsAWhole({ children }: { children: ReactNode }) {
  return <InsideARowReachedAsAWhole.Provider value={true}>{children}</InsideARowReachedAsAWhole.Provider>;
}

/**
 * A click on the bubble must not reach the row around the item — React passes it there, although the bubble is drawn
 * elsewhere on the page —, or a day of the Taxes page would open or close under it.
 */
function keepFromThePageAround(event: SyntheticEvent) {
  event.stopPropagation();
}

/**
 * The one way to explain an item on hover (DESIGN_BRIEF.md §11, round 6), in place of the browser's own `title`: a small
 * bubble after the pointer has rested on the item for 400 ms, at once when the keyboard reaches it, gone when either leaves
 * or on Esc. It is Radix's tooltip: drawn over the page, so no card or scrolling table cuts it, and it never moves the
 * keyboard's focus. An explanation of two sentences or more, or of how a heading's figures are worked out, is a "?" after
 * the heading instead (`HelpOnHover`).
 *
 * @param text       what the bubble says; a line break in a text starts a new line
 * @param placement  above the item (the default) or below it
 * @param align      which of the item's edges the bubble lines up with
 * @param term       a dotted underline under the item: only for a term in running text ("Cash")
 * @param focusable  false when the keyboard shouldn't stop on the item: it is itself a button or a link, or the words
 *                   beside it say the same; left out, the item is a Tab stop unless it is inside a `RowReachedAsAWhole`
 * @param wrapInSpan false to put the hint on the item itself — a single element, which then must take the hint's events:
 *                   a row of the weights card, which the keyboard reaches and highlights as a whole, or text that must
 *                   stay inline to wrap across lines (the highlighted timing note); its own tab stop, if any, stays
 * @param disabled   true while the hint must not show: the item's own panel is open under it (the alerts bell)
 * @param children   the item
 */
export function Hint({
  text,
  placement = 'above',
  align = 'center',
  term = false,
  focusable,
  wrapInSpan = true,
  disabled = false,
  children,
}: {
  text: ReactNode;
  placement?: HintPlacement;
  align?: 'start' | 'center' | 'end';
  term?: boolean;
  focusable?: boolean;
  wrapInSpan?: boolean;
  disabled?: boolean;
  children: ReactNode;
}) {
  const isInsideARowReachedAsAWhole = useContext(InsideARowReachedAsAWhole);
  const isATabStop = focusable ?? !isInsideARowReachedAsAWhole;
  const item = wrapInSpan ? <span className={term ? TERM_CLASS : ITEM_CLASS}>{children}</span> : children;
  return (
    // `open` left out (undefined) lets the tooltip open and close by itself; `false` keeps it closed.
    <Tooltip.Root open={disabled ? false : undefined}>
      {/* The tab stop is given to the item, the span or the element itself; an element's own tabIndex wins. */}
      <Tooltip.Trigger asChild tabIndex={isATabStop ? 0 : undefined}>
        {item}
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content
          side={RADIX_SIDE_OF_PLACEMENT[placement]}
          align={align}
          sideOffset={6}
          onClick={keepFromThePageAround}
          // Above the drawer (z-40) and a modal (z-50), like the "?".
          className="hint-motion z-[60] w-max max-w-[280px] whitespace-pre-line rounded-md border bg-surface-raised px-2.5 py-1.5 text-left text-[13px] font-normal leading-[18px] text-ink shadow-raised"
        >
          {text}
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  );
}
