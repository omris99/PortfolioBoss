import { useState, type FormEvent, type ReactNode } from 'react';
import { addManualPosition, changeManualPosition, errorMessageOf } from '../lib/apiClient';
import {
  INPUT_CLASS,
  isBlank,
  isPositiveWholeNumber,
  localTodayIsoDate,
  numberOrNull,
  trimmedOrNull,
} from '../lib/formInput';
import type { ClosedPosition, ManualPositionRequest, NewManualPositionRequest } from '../types/portfolio';
import { InvestorSelect } from './InvestorSelect';
import { accountOwnerOf, useInvestors } from './InvestorsContext';
import { SECTOR_OPTIONS_LIST_ID } from './SectorCell';

// The lengths of the manual_position columns.
const SYMBOL_MAX_LENGTH = 32;
const CURRENCY_MAX_LENGTH = 8;
const SECTOR_MAX_LENGTH = 60;
const NOTE_MAX_LENGTH = 500;

/** Every holding so far is in dollars, so a new manual position starts in them too. */
const DEFAULT_CURRENCY = 'USD';

/** The API's rule (`Utils.calculateOrderCommission`), said where the field is. */
const COMMISSION_HINT = 'Leave empty for the default: 1 cent a share, at least $5. Enter 0 for no commission.';

const BUTTON_CLASS =
  'rounded-md border border-emerald-500/50 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20 disabled:opacity-50';

// ── what is typed ───────────────────────────────────────────────────────────────────────────────

/** A manual position's own details, as typed. */
interface DetailsValues {
  symbol: string;
  currency: string;
  sector: string;
  note: string;
}

/** A new manual position: its details, and its first buy and sell. Number inputs give text; empty is "not entered". */
interface NewPositionValues extends DetailsValues {
  quantityText: string;
  buyDate: string;
  buyPriceText: string;
  buyCommissionText: string;
  sellDate: string;
  sellPriceText: string;
  sellCommissionText: string;
}

/** Empty: it is a past round trip, so there is no date to guess. */
const EMPTY_NEW_POSITION: NewPositionValues = {
  symbol: '',
  currency: DEFAULT_CURRENCY,
  sector: '',
  note: '',
  quantityText: '',
  buyDate: '',
  buyPriceText: '',
  buyCommissionText: '',
  sellDate: '',
  sellPriceText: '',
  sellCommissionText: '',
};

function detailsValuesOf(closedPosition: ClosedPosition): DetailsValues {
  return {
    symbol: closedPosition.symbol,
    currency: closedPosition.currency,
    sector: closedPosition.sector ?? '',
    note: closedPosition.note ?? '',
  };
}

// ── checks, before a round trip to the API ──────────────────────────────────────────────────────

function problemWithDetails(values: DetailsValues): string | null {
  if (isBlank(values.symbol)) return 'Enter the symbol.';
  if (isBlank(values.currency)) return 'Enter the currency.';
  return null;
}

function isZeroOrMore(typedText: string): boolean {
  return !isBlank(typedText) && Number(typedText) >= 0;
}

/** An empty commission is fine (the default); a typed one must not be negative. */
function isValidCommission(typedText: string): boolean {
  return isBlank(typedText) || Number(typedText) >= 0;
}

/**
 * The same rules the API enforces. A buy date in the future needs no check of its own: the sell date is neither in
 * the future nor before it.
 */
function problemWithNewPosition(values: NewPositionValues, todayIsoDate: string): string | null {
  const detailsProblem = problemWithDetails(values);
  if (detailsProblem !== null) return detailsProblem;
  if (!isPositiveWholeNumber(values.quantityText)) return 'Enter a whole quantity greater than 0.';
  if (values.buyDate === '' || values.sellDate === '') return 'Enter the buy date and the sell date.';
  if (values.sellDate > todayIsoDate) return 'The sell date cannot be in the future.';
  if (values.sellDate < values.buyDate) return 'The sell date cannot be before the buy date.';
  if (!isZeroOrMore(values.buyPriceText) || !isZeroOrMore(values.sellPriceText)) {
    return 'Enter the buy price and the sell price (0 or more).';
  }
  if (!isValidCommission(values.buyCommissionText) || !isValidCommission(values.sellCommissionText)) {
    return 'A commission cannot be negative.';
  }
  return null;
}

// ── what is sent ────────────────────────────────────────────────────────────────────────────────

/** The API stores the symbol and currency in capitals, so "msft" is fine as typed. */
function toManualPositionRequest(values: DetailsValues): ManualPositionRequest {
  return {
    symbol: values.symbol.trim(),
    currency: values.currency.trim(),
    sector: trimmedOrNull(values.sector),
    note: trimmedOrNull(values.note),
  };
}

/** `investorId`: whose first buy and sell they are — the account owner's unless another investor was picked. */
function toNewManualPositionRequest(values: NewPositionValues, investorId: number | null): NewManualPositionRequest {
  return {
    ...toManualPositionRequest(values),
    quantity: Number(values.quantityText),
    buyDate: values.buyDate,
    buyPrice: Number(values.buyPriceText),
    buyCommission: numberOrNull(values.buyCommissionText),
    sellDate: values.sellDate,
    sellPrice: Number(values.sellPriceText),
    sellCommission: numberOrNull(values.sellCommissionText),
    investorId,
  };
}

// ── fields ──────────────────────────────────────────────────────────────────────────────────────

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

/** Symbol, Currency, Sector and Note: the same four fields when adding a manual position and when correcting it. */
function DetailsFields({
  values,
  onChange,
}: {
  values: DetailsValues;
  onChange: (field: keyof DetailsValues, typedText: string) => void;
}) {
  return (
    <>
      <FormField label="Symbol">
        <input
          type="text"
          required
          maxLength={SYMBOL_MAX_LENGTH}
          value={values.symbol}
          onChange={(event) => onChange('symbol', event.target.value)}
          className={`${INPUT_CLASS} w-24 uppercase`}
        />
      </FormField>
      <FormField label="Currency">
        <input
          type="text"
          required
          maxLength={CURRENCY_MAX_LENGTH}
          value={values.currency}
          onChange={(event) => onChange('currency', event.target.value)}
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
          value={values.sector}
          onChange={(event) => onChange('sector', event.target.value)}
          className={`${INPUT_CLASS} w-40`}
        />
      </FormField>
      <FormField label="Note" className="min-w-48 flex-1">
        <input
          type="text"
          maxLength={NOTE_MAX_LENGTH}
          placeholder="Why? (optional)"
          value={values.note}
          onChange={(event) => onChange('note', event.target.value)}
          className={INPUT_CLASS}
        />
      </FormField>
    </>
  );
}

/** A number input for a price, a quantity or a commission. A quantity is a whole number of shares: `isWholeNumber`. */
function AmountInput({
  value,
  required = false,
  isWholeNumber = false,
  placeholder,
  onChange,
}: {
  value: string;
  required?: boolean;
  isWholeNumber?: boolean;
  placeholder?: string;
  onChange: (typedText: string) => void;
}) {
  return (
    <input
      type="number"
      step={isWholeNumber ? '1' : 'any'}
      min={isWholeNumber ? '1' : '0'}
      required={required}
      placeholder={placeholder}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      className={`${INPUT_CLASS} w-28`}
    />
  );
}

// ── the forms ───────────────────────────────────────────────────────────────────────────────────

/**
 * Adds a manual position — one PortfolioBoss never saw as a holding, sold before the first sync — with its first buy
 * and sell of the same quantity. More buys and sells are added in its row afterwards. The API is the authority on what
 * is valid; its message is shown as it is. `onSaved` runs after a successful save and reloads the portfolio.
 */
export function NewManualPositionForm({ onSaved, onCancel }: { onSaved: () => Promise<void>; onCancel: () => void }) {
  const todayIsoDate = localTodayIsoDate();
  const [formValues, setFormValues] = useState(EMPTY_NEW_POSITION);
  const accountOwnerId = accountOwnerOf(useInvestors())?.id ?? null;
  const [investorId, setInvestorId] = useState(accountOwnerId);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const updateFormValue = (field: keyof NewPositionValues, typedText: string) =>
    setFormValues((currentValues) => ({ ...currentValues, [field]: typedText }));

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const problem = problemWithNewPosition(formValues, todayIsoDate);
    if (problem !== null) {
      setErrorMessage(problem);
      return;
    }
    setIsSaving(true);
    setErrorMessage(null);
    try {
      await addManualPosition(toNewManualPositionRequest(formValues, investorId));
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
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Add a closed position</div>
      <p className="text-[11px] text-slate-500">
        A position bought and sold before PortfolioBoss saw it. Start with one buy and one sell; more buys and sells are
        added in its row. A holding PortfolioBoss knows is completed through its trades, in Positions.
      </p>

      <div className="flex flex-wrap items-end gap-2">
        <DetailsFields values={formValues} onChange={updateFormValue} />
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <InvestorSelect investorId={investorId} onChange={setInvestorId} />
        <FormField label="Quantity">
          <AmountInput
            required
            isWholeNumber
            value={formValues.quantityText}
            onChange={(text) => updateFormValue('quantityText', text)}
          />
        </FormField>
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
          <AmountInput required value={formValues.buyPriceText} onChange={(text) => updateFormValue('buyPriceText', text)} />
        </FormField>
        <FormField label="Buy commission" title={COMMISSION_HINT}>
          <AmountInput
            placeholder="default"
            value={formValues.buyCommissionText}
            onChange={(text) => updateFormValue('buyCommissionText', text)}
          />
        </FormField>
      </div>

      <div className="flex flex-wrap items-end gap-2">
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
          <AmountInput required value={formValues.sellPriceText} onChange={(text) => updateFormValue('sellPriceText', text)} />
        </FormField>
        <FormField label="Sell commission" title={COMMISSION_HINT}>
          <AmountInput
            placeholder="default"
            value={formValues.sellCommissionText}
            onChange={(text) => updateFormValue('sellCommissionText', text)}
          />
        </FormField>
        <button type="submit" disabled={isSaving} className={BUTTON_CLASS}>
          {isSaving ? 'Saving…' : 'Add closed position'}
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

/** Corrects a manual position's Symbol, Currency, Sector and Note; its trades are corrected in the list under it. */
export function ManualPositionDetailsForm({
  manualPositionId,
  closedPosition,
  onSaved,
}: {
  manualPositionId: number;
  closedPosition: ClosedPosition;
  onSaved: () => Promise<void>;
}) {
  const [formValues, setFormValues] = useState(() => detailsValuesOf(closedPosition));
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const updateFormValue = (field: keyof DetailsValues, typedText: string) =>
    setFormValues((currentValues) => ({ ...currentValues, [field]: typedText }));

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const problem = problemWithDetails(formValues);
    if (problem !== null) {
      setErrorMessage(problem);
      return;
    }
    setIsSaving(true);
    setErrorMessage(null);
    try {
      await changeManualPosition(manualPositionId, toManualPositionRequest(formValues));
      await onSaved();
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <form onSubmit={(event) => void handleSubmit(event)} className="flex min-w-0 flex-1 flex-col gap-2">
      <div className="flex flex-wrap items-end gap-2">
        <DetailsFields values={formValues} onChange={updateFormValue} />
        <button type="submit" disabled={isSaving} className={BUTTON_CLASS}>
          {isSaving ? 'Saving…' : 'Save details'}
        </button>
      </div>
      {errorMessage && <p className="text-[11px] text-rose-400">{errorMessage}</p>}
    </form>
  );
}
