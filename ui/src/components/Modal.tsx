import { useRef, type ReactNode } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { LoaderCircle, Trash2, X } from 'lucide-react';
import { useSaveAction } from '../hooks/useSaveAction';
import { DANGER_BUTTON_CLASS, LARGE_ICON_BUTTON_CLASS, SECONDARY_BUTTON_CLASS } from '../lib/styles';
import { FormError, SaveButton } from './FormFields';

const SIZE_CLASSES = { narrow: 'w-[420px]', normal: 'w-[480px]', wide: 'w-[680px]' } as const;

/**
 * The frame of every form and confirm dialog: a title and subtitle, the fields, the API's reason under them when it
 * refuses, and Cancel with the action at the bottom. Radix's dialog brings the keyboard and focus handling: focus starts
 * inside, stays inside, Esc or a click on the veil cancels, and the focus goes back where it was. Enter submits.
 * `submitButton` replaces the save button (the confirm dialog's Delete).
 */
export function Modal({
  title,
  subtitle,
  size = 'normal',
  errorMessage,
  isSaving = false,
  saveLabel = 'Save',
  submitButton,
  isAlert = false,
  onSubmit,
  onClose,
  children,
}: {
  title: string;
  subtitle?: ReactNode;
  size?: keyof typeof SIZE_CLASSES;
  errorMessage: string | null;
  isSaving?: boolean;
  saveLabel?: string;
  submitButton?: ReactNode;
  isAlert?: boolean;
  onSubmit: () => void;
  onClose: () => void;
  children: ReactNode;
}) {
  const contentRef = useRef<HTMLDivElement>(null);

  /** The focus starts in the first field, not on ✕; a dialog without fields keeps Radix's choice. */
  const focusFirstField = (event: Event) => {
    const firstField = contentRef.current?.querySelector<HTMLElement>('input:not([tabindex="-1"]), select, textarea');
    if (firstField) {
      event.preventDefault();
      firstField.focus();
    }
  };

  return (
    <Dialog.Root open onOpenChange={(isOpen) => !isOpen && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-scrim-modal" />
        <Dialog.Content
          ref={contentRef}
          onOpenAutoFocus={focusFirstField}
          role={isAlert ? 'alertdialog' : 'dialog'}
          // Radix asks for a description; a modal without a subtitle has none.
          {...(subtitle === undefined ? { 'aria-describedby': undefined } : {})}
          className={`fixed left-1/2 top-[12vh] z-50 max-h-[80vh] max-w-[calc(100vw-48px)] -translate-x-1/2 overflow-y-auto rounded-lg border bg-surface-raised text-ink shadow-raised ${SIZE_CLASSES[size]}`}
        >
          <form
            onSubmit={(event) => {
              event.preventDefault();
              onSubmit();
            }}
          >
            <div className="flex items-start justify-between gap-3 px-6 pb-2 pt-5">
              <div>
                <Dialog.Title className="text-[17px] font-semibold leading-6">{title}</Dialog.Title>
                {subtitle !== undefined && (
                  <Dialog.Description className="mt-0.5 text-[13px] text-muted">{subtitle}</Dialog.Description>
                )}
              </div>
              <Dialog.Close type="button" aria-label="Close" className={`${LARGE_ICON_BUTTON_CLASS} -mr-2 -mt-1`}>
                <X size={18} />
              </Dialog.Close>
            </div>
            <div className="px-6 pb-5 pt-3">
              {children}
              {errorMessage && <FormError>{errorMessage}</FormError>}
            </div>
            <div className="flex items-center justify-end gap-2 rounded-b-lg border-t bg-page px-6 py-4">
              <Dialog.Close type="button" className={SECONDARY_BUTTON_CLASS}>
                Cancel
              </Dialog.Close>
              {submitButton ?? <SaveButton isSaving={isSaving} label={saveLabel} />}
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

/**
 * Asks before deleting: `title` is the question ("Delete this trade?"), `item` the thing itself in one line, `children`
 * what follows from it ("This can't be undone." when omitted). `onConfirm` deletes and reloads; the dialog closes when
 * it succeeds and shows the API's reason when it is refused — a manual position's last sell, say.
 */
export function ConfirmDialog({
  title,
  item,
  children,
  onConfirm,
  onClose,
}: {
  title: string;
  item: ReactNode;
  children?: ReactNode;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}) {
  const { isSaving, errorMessage, runSave } = useSaveAction();

  const handleConfirm = async () => {
    if (await runSave(onConfirm)) onClose();
  };

  const deleteButton = (
    <button type="submit" disabled={isSaving} className={DANGER_BUTTON_CLASS}>
      {isSaving ? (
        <LoaderCircle size={16} className="animate-spin motion-reduce:animate-none" />
      ) : (
        <Trash2 size={16} />
      )}
      {isSaving ? 'Deleting…' : 'Delete'}
    </button>
  );

  return (
    <Modal
      title={title}
      size="narrow"
      isAlert
      errorMessage={errorMessage}
      submitButton={deleteButton}
      onSubmit={() => void handleConfirm()}
      onClose={onClose}
    >
      <div className="mb-3 rounded-md border bg-page px-4 py-3">{item}</div>
      <p className="text-muted">{children ?? 'This can’t be undone.'}</p>
    </Modal>
  );
}
