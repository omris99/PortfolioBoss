import type { ReactNode } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { LARGE_ICON_BUTTON_CLASS } from '../lib/styles';

/** An Esc meant for a field — one being typed in — is not one that closes the drawer. */
function isTypingInAField(target: EventTarget | null): boolean {
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement;
}

/**
 * Every drawer's frame — a holding's, a closed position's, a scanned stock's (`DrawerFrame`), the alert settings: 640px
 * from the right, under the top bar, over a light veil that keeps the page readable. ✕ (`DrawerCloseButton`), Esc or a
 * click outside closes it. Radix's dialog moves the focus in, keeps Tab inside, and gives it back when the drawer
 * closes. What is inside — the header, the tabs, the panel — is the drawer's own (`children`), with a `DrawerTitle`.
 */
export function DrawerShell({
  ariaLabel,
  onClose,
  children,
}: {
  ariaLabel: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <Dialog.Root open onOpenChange={(isOpen) => !isOpen && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-x-0 bottom-0 top-topbar z-40 bg-scrim" />
        <Dialog.Content
          aria-label={ariaLabel}
          aria-describedby={undefined}
          onEscapeKeyDown={(event) => {
            if (isTypingInAField(event.target)) event.preventDefault();
          }}
          className="fixed bottom-0 right-0 top-topbar z-40 flex w-[640px] max-w-full flex-col border-l bg-surface-raised text-ink shadow-raised outline-none"
        >
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

/** The drawer's name — Radix's dialog title, what a screen reader announces when it opens. */
export function DrawerTitle({ children }: { children: ReactNode }) {
  return <Dialog.Title className="text-[22px] font-semibold leading-7 tracking-[-0.01em]">{children}</Dialog.Title>;
}

/** ✕, in the drawer's header. */
export function DrawerCloseButton() {
  return (
    <Dialog.Close aria-label="Close" title="Close (Esc)" className={LARGE_ICON_BUTTON_CLASS}>
      <X size={18} />
    </Dialog.Close>
  );
}
