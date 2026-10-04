import { useState, type FormEvent } from 'react';
import { Pencil, Trash2 } from 'lucide-react';
import { addCashMovement, changeCashMovement, deleteCashMovement, errorMessageOf } from '../lib/apiClient';
import { EMPTY_VALUE, formatMoney } from '../lib/format';
import { INPUT_CLASS, isBlank, localTodayIsoDate, trimmedOrNull } from '../lib/formInput';
import type { CashMovement, CashMovementRequest, CashMovementType, Investor } from '../types/portfolio';

const NOTE_MAX_LENGTH = 500;   // the investor_cash_movement.note column

const TYPE_LABELS: Record<CashMovementType, string> = { DEPOSIT: 'Deposit', WITHDRAWAL: 'Withdrawal' };

// ── the list ────────────────────────────────────────────────────────────────────────────────────

function CashMovementTypeBadge({ type }: { type: CashMovementType }) {
  const colorClass = type === 'DEPOSIT' ? 'bg-emerald-500/15 text-emerald-300' : 'bg-rose-500/15 text-rose-300';
  return <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${colorClass}`}>{TYPE_LABELS[type]}</span>;
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
    <tr className={`border-b border-slate-800/60 last:border-b-0 ${isBeingEdited ? 'bg-emerald-500/5' : ''}`}>
      <td className="px-2 py-1 font-mono">{cashMovement.movementDate}</td>
      <td className="px-2 py-1">
        <CashMovementTypeBadge type={cashMovement.type} />
      </td>
      <td className="px-2 py-1 text-right font-mono">{formatMoney(cashMovement.amount)}</td>
      <td className="px-2 py-1 text-slate-400">{cashMovement.note ?? EMPTY_VALUE}</td>
      <td className="px-2 py-1 text-right">
        <div className="inline-flex gap-1">
          <button
            type="button"
            title="Edit"
            onClick={() => rowActions.onEdit(cashMovement)}
            className="rounded p-1 text-slate-400 transition-colors hover:bg-slate-800 hover:text-slate-100"
          >
            <Pencil size={12} />
          </button>
          <button
            type="button"
            title="Delete"
            onClick={() => rowActions.onDelete(cashMovement)}
            disabled={rowActions.deletingMovementId === cashMovement.id}
            className="rounded p-1 text-slate-400 transition-colors hover:bg-rose-500/10 hover:text-rose-300 disabled:opacity-50"
          >
            <Trash2 size={12} />
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
    <table className="w-full border-collapse">
      <thead>
        <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-500">
          <th className="px-2 py-1 text-left font-medium">Date</th>
          <th className="px-2 py-1 text-left font-medium">Type</th>
          <th className="px-2 py-1 text-right font-medium">Amount</th>
          <th className="px-2 py-1 text-left font-medium">Note</th>
          <th className="px-2 py-1">
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
  const selectedColorClass =
    type === 'DEPOSIT'
      ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-300'
      : 'border-rose-500/60 bg-rose-500/15 text-rose-300';
  return (
    <button
      type="button"
      aria-pressed={isSelected}
      onClick={() => onSelect(type)}
      className={`rounded-md border px-2 py-1 text-[11px] font-semibold transition-colors ${
        isSelected ? selectedColorClass : 'border-slate-700 text-slate-400 hover:text-slate-200'
      }`}
    >
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
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
        {formTitleOf(movementBeingEdited)}
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
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

        <label className="flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
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

        <label className="flex min-w-40 flex-1 flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
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

        <button
          type="submit"
          disabled={isSaving}
          className="rounded-md border border-emerald-500/50 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20 disabled:opacity-50"
        >
          {isSaving ? 'Saving…' : isEditing ? 'Save changes' : 'Add'}
        </button>
        {isEditing && (
          <button
            type="button"
            onClick={onCancelEditing}
            disabled={isSaving}
            className="rounded-md border border-slate-700 px-3 py-1 text-xs text-slate-300 transition-colors hover:text-slate-100 disabled:opacity-50"
          >
            Cancel
          </button>
        )}
      </div>

      {errorMessage && <p className="text-[11px] text-rose-400">{errorMessage}</p>}
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
    <div className="flex flex-col gap-3 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300">
      {investor.cashMovements.length === 0 ? (
        <p className="text-slate-500">No deposits entered yet for {investor.name}.</p>
      ) : (
        <CashMovementList cashMovements={investor.cashMovements} rowActions={rowActions} />
      )}
      {deleteErrorMessage && <p className="text-[11px] text-rose-400">{deleteErrorMessage}</p>}

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
