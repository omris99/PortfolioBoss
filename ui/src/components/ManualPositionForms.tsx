import { useState } from 'react';
import { Trash2 } from 'lucide-react';
import { useSaveAction } from '../hooks/useSaveAction';
import { addManualPosition, changeManualPosition, deleteManualPosition } from '../lib/apiClient';
import {
  INPUT_CLASS,
  isBlank,
  isIsoDate,
  isPositiveWholeNumber,
  localTodayIsoDate,
  numberOrNull,
  trimmedOrNull,
} from '../lib/formInput';
import { NUMBER_INPUT_CLASS, PRIMARY_BUTTON_CLASS, QUIET_DANGER_BUTTON_CLASS } from '../lib/styles';
import type { ClosedPosition, ManualPositionRequest, NewManualPositionRequest } from '../types/portfolio';
import { ClosedPositionTags, missingRealizedPnlReason } from './ClosedPositionsTable';
import { CurrencyFigure } from './CurrencyFigure';
import { DateInput, Field, FormError } from './FormFields';
import { InvestorSelect } from './InvestorSelect';
import { accountOwnerOf, hasSeveralInvestors, useInvestors } from './InvestorsContext';
import { ConfirmDialog, Modal } from './Modal';
import { SECTOR_OPTIONS_LIST_ID } from './SectorCell';

// The lengths of the manual_position columns.
const SYMBOL_MAX_LENGTH = 32;
const CURRENCY_MAX_LENGTH = 8;
const SECTOR_MAX_LENGTH = 60;
const NOTE_MAX_LENGTH = 500;

/** Every holding so far is in dollars, so a new manual position starts in them too. */
const DEFAULT_CURRENCY = 'USD';

const COMMISSION_HINT = 'Empty means the default: 1¢ a share, at least $5 an order. 0 is no commission.';

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

function haveDetailsChanged(values: DetailsValues, closedPosition: ClosedPosition): boolean {
  const storedValues = detailsValuesOf(closedPosition);
  return (Object.keys(values) as (keyof DetailsValues)[]).some((field) => values[field] !== storedValues[field]);
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
  if (!isIsoDate(values.buyDate) || !isIsoDate(values.sellDate)) {
    return 'Enter the buy date and the sell date as yyyy-MM-dd.';
  }
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

function TextInput({
  value,
  maxLength,
  placeholder,
  isUppercase = false,
  onChange,
}: {
  value: string;
  maxLength: number;
  placeholder?: string;
  isUppercase?: boolean;
  onChange: (typedText: string) => void;
}) {
  return (
    <input
      type="text"
      maxLength={maxLength}
      placeholder={placeholder}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      className={`${INPUT_CLASS} w-full ${isUppercase ? 'uppercase' : ''}`}
    />
  );
}

/** A figure: a price, a quantity or a commission, right-aligned. */
function AmountInput({
  value,
  placeholder,
  onChange,
}: {
  value: string;
  placeholder?: string;
  onChange: (typedText: string) => void;
}) {
  return (
    <input
      type="text"
      inputMode="decimal"
      placeholder={placeholder}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-full`}
    />
  );
}

/** The sector, offered from the sectors already entered (the positions table's datalist, rendered by the app). */
function SectorInput({ value, onChange }: { value: string; onChange: (typedText: string) => void }) {
  return (
    <input
      type="text"
      list={SECTOR_OPTIONS_LIST_ID}
      maxLength={SECTOR_MAX_LENGTH}
      value={value}
      onChange={(event) => onChange(event.target.value)}
      className={`${INPUT_CLASS} w-full`}
    />
  );
}

/** The Buy or the Sell of a new closed position: its date, price and commission, in a bordered group. */
function TradeLegFields({
  legend,
  date,
  priceText,
  commissionText,
  minDate,
  maxDate,
  onDateChange,
  onPriceChange,
  onCommissionChange,
}: {
  legend: string;
  date: string;
  priceText: string;
  commissionText: string;
  minDate?: string;
  maxDate: string;
  onDateChange: (date: string) => void;
  onPriceChange: (typedText: string) => void;
  onCommissionChange: (typedText: string) => void;
}) {
  return (
    <fieldset className="m-0 grid min-w-0 grid-cols-[1.3fr_1fr_1fr] gap-3 rounded-lg border px-4 pb-4 pt-3">
      <legend className="-ml-1.5 px-1.5 text-[13px] font-semibold text-ink">{legend}</legend>
      <Field label="Date">
        <DateInput value={date} min={minDate} max={maxDate} onChange={onDateChange} />
      </Field>
      <Field label="Price">
        <AmountInput value={priceText} onChange={onPriceChange} />
      </Field>
      <Field label="Commission" optional>
        <span title={COMMISSION_HINT}>
          <AmountInput value={commissionText} placeholder="Default" onChange={onCommissionChange} />
        </span>
      </Field>
    </fieldset>
  );
}

// ── the forms ───────────────────────────────────────────────────────────────────────────────────

/**
 * "New closed position": one PortfolioBoss never saw as a holding, sold before the first sync — its details, quantity,
 * and its first buy and sell of that quantity. More buys and sells are added in its drawer afterwards. The API is the
 * authority on what is valid; its message is shown as it is. `onSaved` reloads the portfolio, then the modal closes.
 */
export function ClosedPositionModal({ onSaved, onClose }: { onSaved: () => Promise<void>; onClose: () => void }) {
  const todayIsoDate = localTodayIsoDate();
  const investors = useInvestors();
  const [formValues, setFormValues] = useState(EMPTY_NEW_POSITION);
  const [investorId, setInvestorId] = useState(accountOwnerOf(investors)?.id ?? null);
  const { isSaving, errorMessage, setErrorMessage, runSave } = useSaveAction();
  const showsInvestor = hasSeveralInvestors(investors);

  const updateFormValue = (field: keyof NewPositionValues) => (typedText: string) =>
    setFormValues((currentValues) => ({ ...currentValues, [field]: typedText }));

  const handleSubmit = async () => {
    const problem = problemWithNewPosition(formValues, todayIsoDate);
    if (problem !== null) {
      setErrorMessage(problem);
      return;
    }
    const isSaved = await runSave(async () => {
      await addManualPosition(toNewManualPositionRequest(formValues, investorId));
      await onSaved();
    });
    if (isSaved) onClose();
  };

  return (
    <Modal
      title="New closed position"
      subtitle="A position sold before PortfolioBoss existed. It is entered by hand and marked “manual”."
      size="wide"
      errorMessage={errorMessage}
      isSaving={isSaving}
      onSubmit={() => void handleSubmit()}
      onClose={onClose}
    >
      <div className="grid grid-cols-4 gap-4">
        <Field label="Symbol">
          <TextInput
            value={formValues.symbol}
            maxLength={SYMBOL_MAX_LENGTH}
            placeholder="TSLA"
            isUppercase
            onChange={updateFormValue('symbol')}
          />
        </Field>
        <Field label="Currency">
          <TextInput
            value={formValues.currency}
            maxLength={CURRENCY_MAX_LENGTH}
            isUppercase
            onChange={updateFormValue('currency')}
          />
        </Field>
        <Field label="Sector" optional span={2}>
          <SectorInput value={formValues.sector} onChange={updateFormValue('sector')} />
        </Field>
        <Field label="Quantity" hint="Shares bought and sold.">
          <AmountInput value={formValues.quantityText} onChange={updateFormValue('quantityText')} />
        </Field>
        {showsInvestor && (
          <Field label="Investor">
            <InvestorSelect investorId={investorId} onChange={setInvestorId} />
          </Field>
        )}
        <Field label="Note" optional span={showsInvestor ? 2 : 3}>
          <TextInput
            value={formValues.note}
            maxLength={NOTE_MAX_LENGTH}
            placeholder="Why? (optional)"
            onChange={updateFormValue('note')}
          />
        </Field>
      </div>
      <div className="mt-4 grid gap-3">
        <TradeLegFields
          legend="Buy"
          date={formValues.buyDate}
          priceText={formValues.buyPriceText}
          commissionText={formValues.buyCommissionText}
          maxDate={formValues.sellDate || todayIsoDate}
          onDateChange={updateFormValue('buyDate')}
          onPriceChange={updateFormValue('buyPriceText')}
          onCommissionChange={updateFormValue('buyCommissionText')}
        />
        <TradeLegFields
          legend="Sell"
          date={formValues.sellDate}
          priceText={formValues.sellPriceText}
          commissionText={formValues.sellCommissionText}
          minDate={formValues.buyDate || undefined}
          maxDate={todayIsoDate}
          onDateChange={updateFormValue('sellDate')}
          onPriceChange={updateFormValue('sellPriceText')}
          onCommissionChange={updateFormValue('sellCommissionText')}
        />
      </div>
      <p className="mt-2 text-caption text-faint">The sell date can’t be before the buy date.</p>
    </Modal>
  );
}

/** "TSLA · manual · sold 2026-06-18 · +2,640.00 USD": the position being deleted, in one line. */
function ManualPositionLine({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <b className="font-semibold">{closedPosition.symbol}</b>
      <ClosedPositionTags closedPosition={closedPosition} />· sold {closedPosition.closeDate} ·
      {closedPosition.realizedPnl === null ? (
        <span title={missingRealizedPnlReason(closedPosition)}>no P&amp;L</span>
      ) : (
        <CurrencyFigure value={closedPosition.realizedPnl} currency={closedPosition.currency} />
      )}
    </span>
  );
}

/**
 * A manual position's Details tab: its symbol, currency, sector and note, editable — Save changes once something
 * changed — and under them a block that deletes the whole position, its trades with it, after asking.
 */
export function ManualPositionDetails({
  manualPositionId,
  closedPosition,
  onDataChanged,
}: {
  manualPositionId: number;
  closedPosition: ClosedPosition;
  onDataChanged: () => Promise<void>;
}) {
  const [formValues, setFormValues] = useState(() => detailsValuesOf(closedPosition));
  const [isConfirmingDelete, setIsConfirmingDelete] = useState(false);
  const { isSaving, errorMessage, setErrorMessage, runSave } = useSaveAction();
  const tradeCount = closedPosition.trades.length;
  const tradesText = `${tradeCount} ${tradeCount === 1 ? 'trade' : 'trades'}`;

  const updateFormValue = (field: keyof DetailsValues) => (typedText: string) =>
    setFormValues((currentValues) => ({ ...currentValues, [field]: typedText }));

  const handleSave = async () => {
    const problem = problemWithDetails(formValues);
    if (problem !== null) {
      setErrorMessage(problem);
      return;
    }
    await runSave(async () => {
      await changeManualPosition(manualPositionId, toManualPositionRequest(formValues));
      await onDataChanged();
    });
  };

  const deletePositionAndReload = async () => {
    await deleteManualPosition(manualPositionId);
    await onDataChanged();   // the position, and its drawer with it, goes away
  };

  return (
    <div>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          void handleSave();
        }}
      >
        <div className="grid grid-cols-2 gap-4">
          <Field label="Symbol">
            <TextInput value={formValues.symbol} maxLength={SYMBOL_MAX_LENGTH} isUppercase onChange={updateFormValue('symbol')} />
          </Field>
          <Field label="Currency">
            <TextInput
              value={formValues.currency}
              maxLength={CURRENCY_MAX_LENGTH}
              isUppercase
              onChange={updateFormValue('currency')}
            />
          </Field>
          <Field label="Sector" optional span={2}>
            <SectorInput value={formValues.sector} onChange={updateFormValue('sector')} />
          </Field>
          <Field label="Note" optional span={2}>
            <textarea
              rows={3}
              maxLength={NOTE_MAX_LENGTH}
              value={formValues.note}
              onChange={(event) => updateFormValue('note')(event.target.value)}
              className={`${INPUT_CLASS} h-auto w-full resize-y py-1.5 leading-5`}
            />
          </Field>
        </div>
        {errorMessage && <FormError>{errorMessage}</FormError>}
        <div className="mt-3 flex justify-end">
          <button
            type="submit"
            disabled={isSaving || !haveDetailsChanged(formValues, closedPosition)}
            className={PRIMARY_BUTTON_CLASS}
          >
            {isSaving ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>

      <div className="mt-6 flex items-center justify-between gap-4 rounded-lg border border-negative-line px-4 py-3">
        <div>
          <b className="font-semibold">Delete position</b>
          <p className="mt-0.5 text-[13px] text-muted">
            Removes {closedPosition.symbol} and its {tradesText} from Closed. This can’t be undone.
          </p>
        </div>
        <button type="button" onClick={() => setIsConfirmingDelete(true)} className={QUIET_DANGER_BUTTON_CLASS}>
          <Trash2 size={16} />
          Delete position
        </button>
      </div>

      {isConfirmingDelete && (
        <ConfirmDialog
          title={`Delete this position and its ${tradesText}?`}
          item={<ManualPositionLine closedPosition={closedPosition} />}
          onConfirm={deletePositionAndReload}
          onClose={() => setIsConfirmingDelete(false)}
        />
      )}
    </div>
  );
}
