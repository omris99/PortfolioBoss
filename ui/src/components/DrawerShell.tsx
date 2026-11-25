import { createContext, useContext, useRef, type ReactNode } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { useCloseAfterExitAnimation } from '../hooks/animation/useCloseAfterExitAnimation';
import { LARGE_ICON_BUTTON_CLASS } from '../lib/styles';
import { Hint } from './Hint';

/** An Esc meant for a field — one being typed in — is not one that closes the drawer. */
function isTypingInAField(target: EventTarget | null): boolean {
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement;
}

/** A holding's, a closed position's or a scanned stock's drawer is wide; the alert settings' nine lines narrow (round 6). */
type DrawerWidth = 'wide' | 'narrow';

const WIDTH_CLASSES: Record<DrawerWidth, string> = { wide: 'w-[640px]', narrow: 'w-[440px]' };

/** How what is inside a drawer closes it as ✕ does, leaving with its exit: `useCloseDrawer`. */
const CloseDrawerContext = createContext<(() => void) | null>(null);

/**
 * Closes the drawer around the component as ✕ does — it leaves with its exit, and then the page's `onClose` runs —: for
 * a Cancel, or a Save that succeeded. Only inside a `DrawerShell`.
 */
export function useCloseDrawer(): () => void {
  const closeDrawer = useContext(CloseDrawerContext);
  if (closeDrawer === null) throw new Error('useCloseDrawer is called outside a DrawerShell');
  return closeDrawer;
}

/**
 * Every drawer's frame — a holding's, a closed position's, a scanned stock's (`DrawerFrame`), the alert settings: 640px
 * (or `narrow`, 440px) from the right, under the top bar, over a light veil that keeps the page readable. ✕
 * (`DrawerCloseButton`), Esc or a
 * click outside closes it. Radix's dialog keeps Tab inside, and gives the focus back when the drawer closes. On opening
 * the drawer takes the focus itself, not its first button (round 6): the ring of a focused "‹" showed before any key
 * was pressed. The drawer draws no focus ring of its own either — it isn't a control, and opened from its address the
 * ring framed the whole of it —: focused, it keeps its shadow (`focus-visible:shadow-raised`, over index.css's ring).
 * What is inside — the header, the tabs, the panel — is the drawer's own (`children`), with a `DrawerTitle`.
 *
 * It comes in from the right and leaves the same way (round 6's Motion), and `onClose` is called once it has left. The
 * page keeps one shell while a drawer is open and changes only what is inside it, so that ‹ › fade the next item in
 * rather than sliding the whole drawer in again. What is inside closes it the same way through `useCloseDrawer`.
 */
export function DrawerShell({
  ariaLabel,
  width = 'wide',
  onClose,
  children,
}: {
  ariaLabel: string;
  width?: DrawerWidth;
  onClose: () => void;
  children: ReactNode;
}) {
  const contentRef = useRef<HTMLDivElement>(null);
  const { isOpen, startClosing, closeWhenTheExitAnimationEnds } = useCloseAfterExitAnimation(onClose);
  return (
    <Dialog.Root open={isOpen} onOpenChange={(opens) => !opens && startClosing()}>
      <Dialog.Portal>
        <Dialog.Overlay className="drawer-veil-motion fixed inset-x-0 bottom-0 top-topbar z-40 bg-scrim" />
        <Dialog.Content
          ref={contentRef}
          tabIndex={-1}
          aria-label={ariaLabel}
          aria-describedby={undefined}
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            contentRef.current?.focus();
          }}
          onEscapeKeyDown={(event) => {
            if (isTypingInAField(event.target)) event.preventDefault();
          }}
          onAnimationEnd={closeWhenTheExitAnimationEnds}
          className={`drawer-motion fixed bottom-0 right-0 top-topbar z-40 flex ${WIDTH_CLASSES[width]} max-w-full flex-col border-l bg-surface-raised text-ink shadow-raised outline-none focus-visible:shadow-raised`}
        >
          <CloseDrawerContext.Provider value={startClosing}>{children}</CloseDrawerContext.Provider>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

/** The drawer's name — Radix's dialog title, what a screen reader announces when it opens. */
export function DrawerTitle({ children }: { children: ReactNode }) {
  return <Dialog.Title className="text-lg font-semibold leading-7 tracking-[-0.01em]">{children}</Dialog.Title>;
}

/** ✕, in the drawer's header. */
export function DrawerCloseButton() {
  return (
    <Hint text="Close (Esc)" align="end" focusable={false}>
      <Dialog.Close aria-label="Close" className={LARGE_ICON_BUTTON_CLASS}>
        <X size={18} />
      </Dialog.Close>
    </Hint>
  );
}
