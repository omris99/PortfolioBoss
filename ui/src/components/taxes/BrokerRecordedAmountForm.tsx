import { useState } from 'react';
import * as Popover from '@radix-ui/react-popover';
import { Pencil } from 'lucide-react';
import { useSaveAction } from '../../hooks/useSaveAction';
import { cancelTaxConfirmation, confirmTaxDays, correctTaxConfirmation } from '../../lib/apiClient';
import { INPUT_CLASS } from '../../lib/formInput';
import { ICON_BUTTON_CLASS, LINK_BUTTON_CLASS, NUMBER_INPUT_CLASS, SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { SettlementDay } from '../../types/taxes';
import { Field, FormError, SaveButton, Toggle } from '../FormFields';
import { isEverySaleCalculated } from './taxTexts';

type EntryKind = 'Tax' | 'Shield';

/** In shekels, to the agora: "1279.40", "1279", "849.4". */
const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;

const WRONG_AMOUNT_MESSAGE = 'Type the amount in shekels, like 1279.40.';

/** The broker's amount of a day with no sale that can't be calculated — for a day that has one, nothing to start from. */
function amountToStartFrom(settlementDay: SettlementDay): number | null {
  if (settlementDay.confirmation !== null) return settlementDay.confirmation.taxAmount;
  return isEverySaleCalculated(settlementDay) ? settlementDay.computedTaxAmount : null;
}

/** The pencil: outlined in the accent on a day whose amount can only be typed in, quiet everywhere else. */
function pencilClassOf(isTheWayIn: boolean): string {
  return isTheWayIn
    ? 'inline-flex h-7 w-7 items-center justify-center rounded-md border border-accent text-accent hover:bg-accent-soft'
    : ICON_BUTTON_CLASS;
}

/**
 * The form inside the pencil's popover: the entry's kind (only on a day with a sale that can't be calculated — elsewhere
 * the calculation says it), the amount filled with the one confirmed or calculated, and a note. Save confirms the day
 * with the amount typed, or corrects its confirmation; on a confirmed day, "Remove the confirmation" makes it wait again.
 * It is drawn anew on every opening, so it always starts from the day as it is.
 */
function RecordedAmountFields({
  settlementDay,
  onClose,
  onConfirmationChanged,
}: {
  settlementDay: SettlementDay;
  onClose: () => void;
  onConfirmationChanged: () => Promise<void>;
}) {
  const confirmation = settlementDay.confirmation;
  const startingAmount = amountToStartFrom(settlementDay);
  const [entryKind, setEntryKind] = useState<EntryKind>(startingAmount !== null && startingAmount < 0 ? 'Shield' : 'Tax');
  const [typedAmount, setTypedAmount] = useState(startingAmount === null ? '' : Math.abs(startingAmount).toFixed(2));
  const [typedNote, setTypedNote] = useState(confirmation?.note ?? '');
  const { isSaving, errorMessage, setErrorMessage, runSave } = useSaveAction();
  const isEntryKindAsked = !isEverySaleCalculated(settlementDay);
  const isAmountWrong = errorMessage === WRONG_AMOUNT_MESSAGE;

  const saveThenClose = async (save: () => Promise<void>) => {
    const isSaved = await runSave(save);
    if (isSaved) {
      await onConfirmationChanged();
      onClose();
    }
  };

  const saveTypedAmount = () => {
    const trimmedAmount = typedAmount.trim();
    if (!AMOUNT_PATTERN.test(trimmedAmount)) {
      setErrorMessage(WRONG_AMOUNT_MESSAGE);
      return;
    }
    const taxAmount = entryKind === 'Shield' ? -Number(trimmedAmount) : Number(trimmedAmount);
    const note = typedNote.trim() === '' ? null : typedNote.trim();
    void saveThenClose(() =>
      confirmation === null
        ? confirmTaxDays({ confirmations: [{ settlementDay: settlementDay.settlementDay, taxAmount, note }] })
        : correctTaxConfirmation(confirmation.id, { taxAmount, note }),
    );
  };

  const removeTheConfirmation = (confirmationId: number) => {
    void saveThenClose(() => cancelTaxConfirmation(confirmationId));
  };

  // A confirmation is removed, never corrected away: the button appears only on a confirmed day.
  const removeButton = confirmation !== null && (
    <button
      type="button"
      disabled={isSaving}
      onClick={() => removeTheConfirmation(confirmation.id)}
      className={`${LINK_BUTTON_CLASS} ml-auto`}
    >
      Remove the confirmation
    </button>
  );

  return (
    <form
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        saveTypedAmount();
      }}
    >
      <p className="text-section-title">What the broker recorded for {settlementDay.settlementDay}</p>
      <div className="mt-3 flex flex-col gap-3">
        {isEntryKindAsked && (
          <Field label="Entry" isGroup>
            <Toggle label="Entry" options={['Tax', 'Shield'] as EntryKind[]} value={entryKind} onChange={setEntryKind} />
          </Field>
        )}
        <Field label="Amount">
          <span className="flex items-center gap-1.5">
            <span className="font-medium text-muted">₪</span>
            <input
              type="text"
              inputMode="decimal"
              value={typedAmount}
              aria-invalid={isAmountWrong}
              onChange={(event) => setTypedAmount(event.target.value)}
              className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-[120px] aria-[invalid=true]:border-negative`}
            />
          </span>
        </Field>
        <Field label="Note" optional>
          <input
            type="text"
            value={typedNote}
            maxLength={500}
            onChange={(event) => setTypedNote(event.target.value)}
            className={INPUT_CLASS}
          />
        </Field>
      </div>
      {errorMessage !== null && <FormError>{errorMessage}</FormError>}
      <div className="mt-4 flex items-center gap-2">
        <SaveButton isSaving={isSaving} label="Save" />
        <button type="button" onClick={onClose} className={SECONDARY_BUTTON_CLASS}>
          Cancel
        </button>
        {removeButton}
      </div>
    </form>
  );
}

/**
 * The pencil of a day's Check cell and its small form: what the broker recorded for the day, when it isn't the amount
 * PortfolioBoss calculated — or when PortfolioBoss can't calculate it (`isTheWayIn`: then the pencil is outlined). After
 * a save, `onConfirmationChanged` loads the page again.
 */
export function BrokerRecordedAmountForm({
  settlementDay,
  isTheWayIn = false,
  onConfirmationChanged,
}: {
  settlementDay: SettlementDay;
  isTheWayIn?: boolean;
  onConfirmationChanged: () => Promise<void>;
}) {
  const [isOpen, setIsOpen] = useState(false);
  return (
    <Popover.Root open={isOpen} onOpenChange={setIsOpen}>
      <Popover.Trigger
        aria-label={`Type what the broker recorded for ${settlementDay.settlementDay}`}
        className={pencilClassOf(isTheWayIn)}
      >
        <Pencil size={14} />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="end"
          sideOffset={6}
          aria-label={`What the broker recorded for ${settlementDay.settlementDay}`}
          className="z-30 w-[340px] rounded-md border bg-surface-raised px-4 py-3 text-[13px] text-ink shadow-raised"
        >
          <RecordedAmountFields
            settlementDay={settlementDay}
            onClose={() => setIsOpen(false)}
            onConfirmationChanged={onConfirmationChanged}
          />
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
