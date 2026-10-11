import { useState } from 'react';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useSaveAction } from '../hooks/useSaveAction';
import { addCashMovement, changeCashMovement, deleteCashMovement } from '../lib/apiClient';
import { EMPTY_VALUE, formatMoney } from '../lib/format';
import { INPUT_CLASS, isBlank, isIsoDate, localTodayIsoDate, trimmedOrNull } from '../lib/formInput';
import {
  DELETE_ICON_BUTTON_CLASS,
  ICON_BUTTON_CLASS,
  INNER_CELL_CLASS,
  INNER_HEADER_CELL_CLASS,
  INNER_TABLE_CLASS,
  INNER_TABLE_FRAME_CLASS,
  NUMBER_INPUT_CLASS,
  SMALL_SECONDARY_BUTTON_CLASS,
  sideTextClass,
} from '../lib/styles';
import type { CashMovement, CashMovementRequest, CashMovementType, Investor } from '../types/portfolio';
import { DateInput, Field, Toggle } from './FormFields';
import { ConfirmDialog, Modal } from './Modal';

const NOTE_MAX_LENGTH = 500;   // the investor_cash_movement.note column

const CASH_MOVEMENT_TYPES: CashMovementType[] = ['DEPOSIT', 'WITHDRAWAL'];

// ── the modal ───────────────────────────────────────────────────────────────────────────────────

/** What the inputs hold, as typed: a number input gives text, and an empty one means "not entered". */
interface CashMovementFormValues {
  movementDate: string;
  type: CashMovementType;
  amountText: string;
  note: string;
}

function initialFormValues(movementBeingEdited: CashMovement | null, todayIsoDate: string): CashMovementFormValues {
  if (movementBeingEdited === null) {
    return { movementDate: todayIsoDate, type: 'DEPOSIT', amountText: '', note: '' };
  }
  return {
    movementDate: movementBeingEdited.movementDate,
    type: movementBeingEdited.type,
    amountText: String(movementBeingEdited.amount),
    note: movementBeingEdited.note ?? '',
  };
}

/** The same rules the API enforces, checked first so the common mistakes don't need a round trip. */
function problemWithForm(formValues: CashMovementFormValues, todayIsoDate: string): string | null {
  if (!isIsoDate(formValues.movementDate)) return 'Enter the date as yyyy-MM-dd.';
  if (formValues.movementDate > todayIsoDate) return 'The date cannot be in the future.';
  if (isBlank(formValues.amountText) || !(Number(formValues.amountText) > 0)) {
    return 'Enter an amount greater than 0.';
  }
  return null;
}

function toCashMovementRequest(formValues: CashMovementFormValues): CashMovementRequest {
  return {
    movementDate: formValues.movementDate,
    type: formValues.type,
    amount: Number(formValues.amountText),
    note: trimmedOrNull(formValues.note),
  };
}

/** "deposit" or "withdrawal", in a sentence. */
function typeWordOf(type: CashMovementType): string {
  return type.toLowerCase();
}

/**
 * Adds a deposit or withdrawal for `investor`, or corrects `movementBeingEdited` when one is given. The API is the
 * authority on what is valid; its message is shown as it is. `onSaved` reloads the portfolio, then the modal closes.
 */
function CashMovementModal({
  investor,
  movementBeingEdited,
  onSaved,
  onClose,
}: {
  investor: Investor;
  movementBeingEdited: CashMovement | null;
  onSaved: () => Promise<void>;
  onClose: () => void;
}) {
  const todayIsoDate = localTodayIsoDate();
  const [formValues, setFormValues] = useState(() => initialFormValues(movementBeingEdited, todayIsoDate));
  const { isSaving, errorMessage, setErrorMessage, runSave } = useSaveAction();
  const isEditing = movementBeingEdited !== null;

  const updateFormValue = <Field extends keyof CashMovementFormValues>(
    field: Field,
    value: CashMovementFormValues[Field],
  ) => setFormValues((currentValues) => ({ ...currentValues, [field]: value }));

  const handleSubmit = async () => {
    const problem = problemWithForm(formValues, todayIsoDate);
    if (problem !== null) {
      setErrorMessage(problem);
      return;
    }
    const cashMovementRequest = toCashMovementRequest(formValues);
    const isSaved = await runSave(async () => {
      if (movementBeingEdited === null) {
        await addCashMovement(investor.id, cashMovementRequest);
      } else {
        await changeCashMovement(movementBeingEdited.id, cashMovementRequest);
      }
      await onSaved();
    });
    if (isSaved) onClose();
  };

  return (
    <Modal
      title={isEditing ? `Edit ${typeWordOf(formValues.type)}` : 'Add deposit or withdrawal'}
      subtitle={investor.name}
      size="narrow"
      errorMessage={errorMessage}
      isSaving={isSaving}
      saveLabel={isEditing ? 'Save changes' : 'Save'}
      onSubmit={() => void handleSubmit()}
      onClose={onClose}
    >
      <div className="grid grid-cols-2 gap-4">
        <Field label="Type" span={2} isGroup>
          <Toggle
            label="Deposit or withdrawal"
            options={CASH_MOVEMENT_TYPES}
            value={formValues.type}
            onChange={(type) => updateFormValue('type', type)}
          />
        </Field>
        <Field label="Date">
          <DateInput
            value={formValues.movementDate}
            max={todayIsoDate}
            onChange={(movementDate) => updateFormValue('movementDate', movementDate)}
          />
        </Field>
        <Field label="Amount (USD)">
          <input
            type="text"
            inputMode="decimal"
            value={formValues.amountText}
            onChange={(event) => updateFormValue('amountText', event.target.value)}
            className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-full`}
          />
        </Field>
        <Field label="Note" optional span={2}>
          <input
            type="text"
            maxLength={NOTE_MAX_LENGTH}
            value={formValues.note}
            onChange={(event) => updateFormValue('note', event.target.value)}
            className={`${INPUT_CLASS} w-full`}
          />
        </Field>
      </div>
    </Modal>
  );
}

// ── the list ────────────────────────────────────────────────────────────────────────────────────

function CashMovementRow({
  cashMovement,
  onEdit,
  onDelete,
}: {
  cashMovement: CashMovement;
  onEdit: () => void;
  onDelete: () => void;
}) {
  return (
    <tr>
      <td className={INNER_CELL_CLASS}>{cashMovement.movementDate}</td>
      <td className={INNER_CELL_CLASS}>
        <span className={sideTextClass(cashMovement.type === 'WITHDRAWAL')}>{cashMovement.type}</span>
      </td>
      <td className={`${INNER_CELL_CLASS} text-right`}>{formatMoney(cashMovement.amount)}</td>
      <td title={cashMovement.note ?? undefined} className={`${INNER_CELL_CLASS} max-w-[120px] truncate`}>
        {cashMovement.note ?? <span className="text-faint">{EMPTY_VALUE}</span>}
      </td>
      <td className={`${INNER_CELL_CLASS} w-14 text-right`}>
        <span className="inline-flex">
          <button type="button" aria-label="Edit" title="Edit" onClick={onEdit} className={ICON_BUTTON_CLASS}>
            <Pencil size={14} />
          </button>
          <button type="button" aria-label="Delete" title="Delete" onClick={onDelete} className={DELETE_ICON_BUTTON_CLASS}>
            <Trash2 size={14} />
          </button>
        </span>
      </td>
    </tr>
  );
}

/** The deposit or withdrawal being added or corrected in the modal, or none. */
type CashMovementModalState = { movementBeingEdited: CashMovement | null } | null;

/**
 * An investor's deposits and withdrawals — the money that went into the IB account for them, or came out — with ✏️ and
 * 🗑 and an Add button. Never the account owner's: their cash comes from IB. Every change reloads the whole portfolio,
 * since their cash, and the account owner's, are derived from these on the server.
 */
export function CashMovementsList({ investor, onDataChanged }: { investor: Investor; onDataChanged: () => Promise<void> }) {
  const [cashMovementModal, setCashMovementModal] = useState<CashMovementModalState>(null);
  const [movementToDelete, setMovementToDelete] = useState<CashMovement | null>(null);

  const deleteAndReload = async (cashMovement: CashMovement) => {
    await deleteCashMovement(cashMovement.id);
    await onDataChanged();
  };

  return (
    <div className="flex flex-col items-start gap-2">
      {investor.cashMovements.length === 0 ? (
        <p className="text-muted">No deposits or withdrawals entered yet.</p>
      ) : (
        <div className={`${INNER_TABLE_FRAME_CLASS} w-full`}>
          <table className={INNER_TABLE_CLASS}>
            <thead>
              <tr>
                <th className={`${INNER_HEADER_CELL_CLASS} font-medium`}>Date</th>
                <th className={`${INNER_HEADER_CELL_CLASS} font-medium`}>Type</th>
                <th className={`${INNER_HEADER_CELL_CLASS} text-right font-medium`}>Amount</th>
                <th className={`${INNER_HEADER_CELL_CLASS} font-medium`}>Note</th>
                <th className={INNER_HEADER_CELL_CLASS}>
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {investor.cashMovements.map((cashMovement) => (
                <CashMovementRow
                  key={cashMovement.id}
                  cashMovement={cashMovement}
                  onEdit={() => setCashMovementModal({ movementBeingEdited: cashMovement })}
                  onDelete={() => setMovementToDelete(cashMovement)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <button
        type="button"
        onClick={() => setCashMovementModal({ movementBeingEdited: null })}
        className={SMALL_SECONDARY_BUTTON_CLASS}
      >
        <Plus size={14} />
        Add
      </button>

      {cashMovementModal !== null && (
        <CashMovementModal
          investor={investor}
          movementBeingEdited={cashMovementModal.movementBeingEdited}
          onSaved={onDataChanged}
          onClose={() => setCashMovementModal(null)}
        />
      )}
      {movementToDelete !== null && (
        <ConfirmDialog
          title={`Delete this ${typeWordOf(movementToDelete.type)}?`}
          item={`${movementToDelete.movementDate} · ${movementToDelete.type} ${formatMoney(movementToDelete.amount)} · ${investor.name}`}
          onConfirm={() => deleteAndReload(movementToDelete)}
          onClose={() => setMovementToDelete(null)}
        >
          {investor.name}’s cash and Deposited change by this amount. This can’t be undone.
        </ConfirmDialog>
      )}
    </div>
  );
}
