import { useState, type FormEvent } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import { addCashMovement, changeCashMovement, deleteCashMovement, errorMessageOf } from '../lib/apiClient';
import { EMPTY_VALUE, formatMoney } from '../lib/format';
import { INPUT_CLASS, isBlank, localTodayIsoDate, trimmedOrNull } from '../lib/formInput';
import {
  CHIP_CLASS,
  DELETE_ICON_BUTTON_CLASS,
  ERROR_TEXT_CLASS,
  FIELD_LABEL_CLASS,
  ICON_BUTTON_CLASS,
  PANEL_CLASS,
  PANEL_TITLE_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECONDARY_BUTTON_CLASS,
  TABLE_CLASS,
  TABLE_HEAD_ROW_CLASS,
  TABLE_ROW_CLASS,
  toggleButtonClass,
} from '../lib/styles';
import type { CashMovement, CashMovementRequest, CashMovementType, Investor } from '../types/portfolio';

const NOTE_MAX_LENGTH = 500;   // the investor_cash_movement.note column

const TYPE_LABELS: Record<CashMovementType, string> = { DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal' };

// ── the list ────────────────────────────────────────────────────────────────────────────────────

/** Grey for both, like BUY and SELL: in the design only a profit or a loss is green or red. */
function CashMovementTypeBadge({ type }: { type: CashMovementType }) {
  return <span className={`${CHIP_CLASS} bg-subtle text-ink`}>{TYPE_LABELS[type]}</span>;
}

/** What the ✏️ and 🗑 of each row do, as in the trades list. */
interface CashMovementRowActions {
  movementIdBeingEdited: number | null;
  deletingMovementId: number | null;
  onEdit: (cashMovement: CashMovement) => void;
  onDelete: (cashMovement: CashMovement) => void;
}

function CashMovementRow({
  cashMovement,
  rowActions,
}: {
  cashMovement: CashMovement;
  rowActions: CashMovementRowActions;
}) {
  const isBeingEdited = rowActions.movementIdBeingEdited === cashMovement.id;
  return (
    <tr className={`${TABLE_ROW_CLASS} ${isBeingEdited ? 'bg-accent-soft' : ''}`}>
      <td className="px-3 py-1.5">{cashMovement.movementDate}</td>
      <td className="px-3 py-1.5">
        <CashMovementTypeBadge type={cashMovement.type} />
      </td>
      <td className="px-3 py-1.5 text-right">{formatMoney(cashMovement.amount)}</td>
      <td className="px-3 py-1.5 text-muted">{cashMovement.note ?? EMPTY_VALUE}</td>
      <td className="px-3 py-1 text-right">
        <div className="inline-flex gap-1">
          <button
            type="button"
            title="Edit"
            onClick={() => rowActions.onEdit(cashMovement)}
            className={ICON_BUTTON_CLASS}
          >
            <Pencil size={14} />
          </button>
          <button
            type="button"
            title="Delete"
            onClick={() => rowActions.onDelete(cashMovement)}
            disabled={rowActions.deletingMovementId === cashMovement.id}
            className={DELETE_ICON_BUTTON_CLASS}
          >
            <Trash2 size={14} />
          </button>
        </div>
      </td>
    </tr>
  );
}

function CashMovementList({
  cashMovements,
  rowActions,
}: {
  cashMovements: CashMovement[];
  rowActions: CashMovementRowActions;
}) {
  return (
    <table className={TABLE_CLASS}>
      <thead>
        <tr className={TABLE_HEAD_ROW_CLASS}>
          <th className="px-3 py-1.5 text-left font-medium">Date</th>
          <th className="px-3 py-1.5 text-left font-medium">Type</th>
          <th className="px-3 py-1.5 text-right font-medium">Amount</th>
          <th className="px-3 py-1.5 text-left font-medium">Note</th>
          <th className="px-3 py-1.5">
            <span className="sr-only">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {cashMovements.map((cashMovement) => (
          <CashMovementRow key={cashMovement.id} cashMovement={cashMovement} rowActions={rowActions} />
        ))}
      </tbody>
    </table>
  );
}

// ── the form ────────────────────────────────────────────────────────────────────────────────────

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
  if (formValues.movementDate === '') return 'Enter the date.';
  if (formValues.movementDate > todayIsoDate) return 'The date cannot be in the future.';
  if (isBlank(formValues.amountText) || !(Number(formValues.amountText) > 0)) {
    return 'Enter an amount greater than 0.';
  }
  return null;
}

/** "Add a deposit or withdrawal", or "Edit the deposit of 2026-01-10". */
function formTitleOf(movementBeingEdited: CashMovement | null): string {
  if (movementBeingEdited === null) return 'Add a deposit or withdrawal';
  return `Edit the ${TYPE_LABELS[movementBeingEdited.type].toLowerCase()} of ${movementBeingEdited.movementDate}`;
}

function toCashMovementRequest(formValues: CashMovementFormValues): CashMovementRequest {
  return {
    movementDate: formValues.movementDate,
    type: formValues.type,
    amount: Number(formValues.amountText),
    note: trimmedOrNull(formValues.note),
  };
}

function TypeButton({
  type,
  selectedType,
  onSelect,
}: {
  type: CashMovementType;
  selectedType: CashMovementType;
  onSelect: (type: CashMovementType) => void;
}) {
  const isSelected = type === selectedType;
  return (
    <button type="button" aria-pressed={isSelected} onClick={() => onSelect(type)} className={toggleButtonClass(isSelected)}>
      {TYPE_LABELS[type]}
    </button>
  );
}

/**
 * Adds a deposit or withdrawal for `investorId`, or corrects `movementBeingEdited` when one is given. The API is the
 * authority on what is valid; its message is shown as it is. `onSaved` reloads the portfolio after a successful save.
 */
function CashMovementForm({
  investorId,
  movementBeingEdited,
  onSaved,
  onCancelEditing,
}: {
  investorId: number;
  movementBeingEdited: CashMovement | null;
  onSaved: () => Promise<void>;
  onCancelEditing: () => void;
}) {
  const todayIsoDate = localTodayIsoDate();
  const [formValues, setFormValues] = useState(() => initialFormValues(movementBeingEdited, todayIsoDate));
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isEditing = movementBeingEdited !== null;

  const updateFormValue = <Field extends keyof CashMovementFormValues>(
    field: Field,
    value: CashMovementFormValues[Field],
  ) => setFormValues((currentValues) => ({ ...currentValues, [field]: value }));

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const problem = problemWithForm(formValues, todayIsoDate);
    if (problem !== null) {
      setErrorMessage(problem);
      return;
    }
    setIsSaving(true);
    setErrorMessage(null);
    try {
      const cashMovementRequest = toCashMovementRequest(formValues);
      if (movementBeingEdited === null) {
        await addCashMovement(investorId, cashMovementRequest);
      } else {
        await changeCashMovement(movementBeingEdited.id, cashMovementRequest);
      }
      await onSaved();
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <form onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-2">
      <div className={PANEL_TITLE_CLASS}>
        {formTitleOf(movementBeingEdited)}
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <label className={FIELD_LABEL_CLASS}>
          Date
          <input
            type="date"
            required
            max={todayIsoDate}
            value={formValues.movementDate}
            onChange={(event) => updateFormValue('movementDate', event.target.value)}
            className={INPUT_CLASS}
          />
        </label>

        <div className="flex gap-1" role="group" aria-label="Deposit or withdrawal">
          <TypeButton type="DEPOSIT" selectedType={formValues.type} onSelect={(type) => updateFormValue('type', type)} />
          <TypeButton type="WITHDRAWAL" selectedType={formValues.type} onSelect={(type) => updateFormValue('type', type)} />
        </div>

        <label className={FIELD_LABEL_CLASS}>
          Amount (USD)
          <input
            type="number"
            step="any"
            min="0"
            required
            value={formValues.amountText}
            onChange={(event) => updateFormValue('amountText', event.target.value)}
            className={`${INPUT_CLASS} w-28`}
          />
        </label>

        <label className={`min-w-40 flex-1 ${FIELD_LABEL_CLASS}`}>
          Note
          <input
            type="text"
            maxLength={NOTE_MAX_LENGTH}
            placeholder="optional"
            value={formValues.note}
            onChange={(event) => updateFormValue('note', event.target.value)}
            className={INPUT_CLASS}
          />
        </label>

        <button type="submit" disabled={isSaving} className={PRIMARY_BUTTON_CLASS}>
          {isSaving ? 'Saving…' : isEditing ? 'Save changes' : 'Add'}
        </button>
        {isEditing && (
          <button
            type="button"
            onClick={onCancelEditing}
            disabled={isSaving}
            className={SECONDARY_BUTTON_CLASS}
          >
            Cancel
          </button>
        )}
      </div>

      {errorMessage && <p className={ERROR_TEXT_CLASS}>{errorMessage}</p>}
    </form>
  );
}

// ── the panel ───────────────────────────────────────────────────────────────────────────────────

function confirmDeletion(cashMovement: CashMovement): boolean {
  return window.confirm(
    `Delete the ${TYPE_LABELS[cashMovement.type].toLowerCase()} of ${formatMoney(cashMovement.amount)} on ` +
      `${cashMovement.movementDate}?`,
  );
}

/**
 * An investor's deposits and withdrawals — the money that went into the IB account for them, or came out — and the
 * form under them. Never the account owner's: their cash comes from IB. Every change reloads the whole portfolio,
 * since their cash, and the account owner's, are derived from these on the server.
 */
export function CashMovementsPanel({
  investor,
  onDataChanged,
}: {
  investor: Investor;
  onDataChanged: () => Promise<void>;
}) {
  const [movementBeingEdited, setMovementBeingEdited] = useState<CashMovement | null>(null);
  // A new key remounts the form, so it starts again from fresh values after each save.
  const [formGeneration, setFormGeneration] = useState(0);
  const [deletingMovementId, setDeletingMovementId] = useState<number | null>(null);
  const [deleteErrorMessage, setDeleteErrorMessage] = useState<string | null>(null);

  const resetForm = () => {
    setMovementBeingEdited(null);
    setFormGeneration((generation) => generation + 1);
  };

  const handleSaved = async () => {
    await onDataChanged();
    resetForm();
  };

  const handleDelete = async (cashMovement: CashMovement) => {
    if (!confirmDeletion(cashMovement)) return;
    setDeletingMovementId(cashMovement.id);
    setDeleteErrorMessage(null);
    try {
      await deleteCashMovement(cashMovement.id);
      await onDataChanged();
      if (movementBeingEdited?.id === cashMovement.id) resetForm();
    } catch (error) {
      setDeleteErrorMessage(errorMessageOf(error));
    } finally {
      setDeletingMovementId(null);
    }
  };

  const rowActions: CashMovementRowActions = {
    movementIdBeingEdited: movementBeingEdited?.id ?? null,
    deletingMovementId,
    onEdit: setMovementBeingEdited,
    onDelete: (cashMovement) => void handleDelete(cashMovement),
  };
  const formKey = movementBeingEdited === null ? `new-${formGeneration}` : `edit-${movementBeingEdited.id}`;

  return (
    <div className={PANEL_CLASS}>
      {investor.cashMovements.length === 0 ? (
        <p className="text-muted">No deposits entered yet for {investor.name}.</p>
      ) : (
        <CashMovementList cashMovements={investor.cashMovements} rowActions={rowActions} />
      )}
      {deleteErrorMessage && <p className={ERROR_TEXT_CLASS}>{deleteErrorMessage}</p>}

      <CashMovementForm
        key={formKey}
        investorId={investor.id}
        movementBeingEdited={movementBeingEdited}
        onSaved={handleSaved}
        onCancelEditing={resetForm}
      />
    </div>
  );
}
