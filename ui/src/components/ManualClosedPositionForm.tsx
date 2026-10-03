import { useState, type FormEvent, type ReactNode } from 'react';
import { addManualClosedPosition, changeManualClosedPosition, errorMessageOf } from '../lib/apiClient';
import { INPUT_CLASS, isBlank, localTodayIsoDate, numberOrNull, trimmedOrNull } from '../lib/formInput';
import type { ClosedPosition, ManualClosedPositionRequest } from '../types/portfolio';
import { SECTOR_OPTIONS_LIST_ID } from './SectorCell';

// The lengths of the manual_closed_position columns.
const SYMBOL_MAX_LENGTH = 32;
const CURRENCY_MAX_LENGTH = 8;
const SECTOR_MAX_LENGTH = 60;
const NOTE_MAX_LENGTH = 500;

/** Every holding so far is in dollars, so a new row starts in them too. */
const DEFAULT_CURRENCY = 'USD';

/** The API's rule (`Utils.calculateOrderCommission`, once for the buy and once for the sell), said where the field is. */
const COMMISSION_HINT =
  'Leave empty for the default of a buy and a sell: 1 cent a share each, at least $5 each — worked out again ' +
  'from the quantity on every save. Enter 0 for no commission.';

/** What the inputs hold, as typed: number inputs give text, and an empty one means "not entered". */
interface ManualClosedPositionFormValues {
  symbol: string;
  currency: string;
  sector: string;
  quantityText: string;
  buyDate: string;
  buyPriceText: string;
  sellDate: string;
  sellPriceText: string;
  commissionText: string;
  note: string;
}

function textOf(amount: number | null): string {
  return amount === null ? '' : String(amount);
}

/** Empty for a new row — it is a past round trip, so there is no date to guess — or the values of the row edited. */
function initialFormValues(rowBeingEdited: ClosedPosition | null): ManualClosedPositionFormValues {
  if (rowBeingEdited === null) {
    return {
      symbol: '',
      currency: DEFAULT_CURRENCY,
      sector: '',
      quantityText: '',
      buyDate: '',
      buyPriceText: '',
      sellDate: '',
      sellPriceText: '',
      commissionText: '',
      note: '',
    };
  }
  return {
    symbol: rowBeingEdited.symbol,
    currency: rowBeingEdited.currency,
    sector: rowBeingEdited.sector ?? '',
    quantityText: String(rowBeingEdited.quantity),
    buyDate: rowBeingEdited.openDate,
    // A manual row has one buy and one sell, so their averages are their prices.
    buyPriceText: textOf(rowBeingEdited.averageBuyPrice),
    sellDate: rowBeingEdited.closeDate,
    sellPriceText: textOf(rowBeingEdited.averageSellPrice),
    commissionText: String(rowBeingEdited.commissions),
    note: rowBeingEdited.note ?? '',
  };
}

function isZeroOrMore(typedText: string): boolean {
  return !isBlank(typedText) && Number(typedText) >= 0;
}

/**
 * The same rules the API enforces, checked first so the common mistakes don't need a round trip. A buy date in the
 * future needs no check of its own: the sell date is neither in the future nor before it.
 */
function problemWithForm(formValues: ManualClosedPositionFormValues, todayIsoDate: string): string | null {
  if (isBlank(formValues.symbol)) return 'Enter the symbol.';
  if (isBlank(formValues.currency)) return 'Enter the currency.';
  if (isBlank(formValues.quantityText) || !(Number(formValues.quantityText) > 0)) {
    return 'Enter a quantity greater than 0.';
  }
  if (formValues.buyDate === '' || formValues.sellDate === '') return 'Enter the buy date and the sell date.';
  if (formValues.sellDate > todayIsoDate) return 'The sell date cannot be in the future.';
  if (formValues.sellDate < formValues.buyDate) return 'The sell date cannot be before the buy date.';
  if (!isZeroOrMore(formValues.buyPriceText) || !isZeroOrMore(formValues.sellPriceText)) {
    return 'Enter the buy price and the sell price (0 or more).';
  }
  if (!isBlank(formValues.commissionText) && !(Number(formValues.commissionText) >= 0)) {
    return 'The commission cannot be negative.';
  }
  return null;
}

/** The API stores the symbol and currency in capitals, so "msft" is fine as typed. */
function toManualClosedPositionRequest(formValues: ManualClosedPositionFormValues): ManualClosedPositionRequest {
  return {
    symbol: formValues.symbol.trim(),
    currency: formValues.currency.trim(),
    sector: trimmedOrNull(formValues.sector),
    quantity: Number(formValues.quantityText),
    buyDate: formValues.buyDate,
    buyPrice: Number(formValues.buyPriceText),
    sellDate: formValues.sellDate,
    sellPrice: Number(formValues.sellPriceText),
    commission: numberOrNull(formValues.commissionText),
    note: trimmedOrNull(formValues.note),
  };
}

function FormField({
  label,
  title,
  className = '',
  children,
}: {
  label: string;
  title?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <label
      title={title}
      className={`flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500 ${className}`}
    >
      {label}
      {children}
    </label>
  );
}

/**
 * Adds a closed position by hand — one PortfolioBoss never saw as a holding, sold before the first sync — or corrects
 * `rowBeingEdited`, a row added that way. The API is the authority on what is valid; its message is shown as it is.
 * `onSaved` runs after a successful save and reloads the portfolio.
 */
export function ManualClosedPositionForm({
  rowBeingEdited,
  onSaved,
  onCancel,
}: {
  rowBeingEdited: ClosedPosition | null;
  onSaved: () => Promise<void>;
  onCancel: () => void;
}) {
  const todayIsoDate = localTodayIsoDate();
  const [formValues, setFormValues] = useState(() => initialFormValues(rowBeingEdited));
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const manualClosedPositionId = rowBeingEdited?.manualClosedPositionId ?? null;
  const isEditing = manualClosedPositionId !== null;

  const updateFormValue = <Field extends keyof ManualClosedPositionFormValues>(
    field: Field,
    value: ManualClosedPositionFormValues[Field],
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
      const request = toManualClosedPositionRequest(formValues);
      if (manualClosedPositionId === null) {
        await addManualClosedPosition(request);
      } else {
        await changeManualClosedPosition(manualClosedPositionId, request);
      }
      await onSaved();
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <form
      onSubmit={(event) => void handleSubmit(event)}
      className="flex flex-col gap-2 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300"
    >
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">
        {isEditing ? `Edit the closed position of ${rowBeingEdited?.symbol}` : 'Add a closed position'}
      </div>
      <p className="text-[11px] text-slate-500">
        A position bought and sold before PortfolioBoss saw it, as one buy and one sell. A holding PortfolioBoss
        knows is completed through its trades, in Positions.
      </p>

      <div className="flex flex-wrap items-end gap-2">
        <FormField label="Symbol">
          <input
            type="text"
            required
            maxLength={SYMBOL_MAX_LENGTH}
            value={formValues.symbol}
            onChange={(event) => updateFormValue('symbol', event.target.value)}
            className={`${INPUT_CLASS} w-24 uppercase`}
          />
        </FormField>
        <FormField label="Currency">
          <input
            type="text"
            required
            maxLength={CURRENCY_MAX_LENGTH}
            value={formValues.currency}
            onChange={(event) => updateFormValue('currency', event.target.value)}
            className={`${INPUT_CLASS} w-16 uppercase`}
          />
        </FormField>
        <FormField label="Sector">
          {/* The sectors already entered, from the datalist the positions table renders. */}
          <input
            type="text"
            list={SECTOR_OPTIONS_LIST_ID}
            maxLength={SECTOR_MAX_LENGTH}
            placeholder="optional"
            value={formValues.sector}
            onChange={(event) => updateFormValue('sector', event.target.value)}
            className={`${INPUT_CLASS} w-40`}
          />
        </FormField>
        <FormField label="Quantity">
          <input
            type="number"
            step="any"
            min="0"
            required
            value={formValues.quantityText}
            onChange={(event) => updateFormValue('quantityText', event.target.value)}
            className={`${INPUT_CLASS} w-28`}
          />
        </FormField>
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <FormField label="Buy date">
          <input
            type="date"
            required
            max={formValues.sellDate || todayIsoDate}
            value={formValues.buyDate}
            onChange={(event) => updateFormValue('buyDate', event.target.value)}
            className={INPUT_CLASS}
          />
        </FormField>
        <FormField label="Buy price">
          <input
            type="number"
            step="any"
            min="0"
            required
            value={formValues.buyPriceText}
            onChange={(event) => updateFormValue('buyPriceText', event.target.value)}
            className={`${INPUT_CLASS} w-28`}
          />
        </FormField>
        <FormField label="Sell date">
          <input
            type="date"
            required
            min={formValues.buyDate || undefined}
            max={todayIsoDate}
            value={formValues.sellDate}
            onChange={(event) => updateFormValue('sellDate', event.target.value)}
            className={INPUT_CLASS}
          />
        </FormField>
        <FormField label="Sell price">
          <input
            type="number"
            step="any"
            min="0"
            required
            value={formValues.sellPriceText}
            onChange={(event) => updateFormValue('sellPriceText', event.target.value)}
            className={`${INPUT_CLASS} w-28`}
          />
        </FormField>
        <FormField label="Commission" title={COMMISSION_HINT}>
          <input
            type="number"
            step="any"
            min="0"
            placeholder="default"
            value={formValues.commissionText}
            onChange={(event) => updateFormValue('commissionText', event.target.value)}
            className={`${INPUT_CLASS} w-24`}
          />
        </FormField>
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <FormField label="Note" className="min-w-48 flex-1">
          <input
            type="text"
            maxLength={NOTE_MAX_LENGTH}
            placeholder="Why? (optional)"
            value={formValues.note}
            onChange={(event) => updateFormValue('note', event.target.value)}
            className={INPUT_CLASS}
          />
        </FormField>
        <button
          type="submit"
          disabled={isSaving}
          className="rounded-md border border-emerald-500/50 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20 disabled:opacity-50"
        >
          {isSaving ? 'Saving…' : isEditing ? 'Save changes' : 'Add closed position'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={isSaving}
          className="rounded-md border border-slate-700 px-3 py-1 text-xs text-slate-300 transition-colors hover:text-slate-100 disabled:opacity-50"
        >
          Cancel
        </button>
      </div>

      {errorMessage && <p className="text-[11px] text-rose-400">{errorMessage}</p>}
    </form>
  );
}
