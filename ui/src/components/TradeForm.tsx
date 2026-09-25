import { useState, type FormEvent } from 'react';
import { addTrade, changeTrade, errorMessageOf } from '../lib/apiClient';
import type { Holding, Trade, TradeRequest, TradeSide } from '../types/portfolio';

const NOTE_MAX_LENGTH = 500;   // the trade.note column

/** What the inputs hold, as typed: number inputs give text, and an empty one means "not entered". */
interface TradeFormValues {
  tradeDate: string;
  side: TradeSide;
  quantityText: string;
  priceText: string;
  note: string;
}

/**
 * Today as 'yyyy-MM-dd' in the local time zone — the same "today" the API checks the date against. Not
 * `toISOString()`, which is UTC: just after midnight in Israel it is still yesterday there.
 */
function localTodayIsoDate(): string {
  const today = new Date();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${today.getFullYear()}-${month}-${day}`;
}

/**
 * IB's average cost spreads the commissions over every share, so it comes with many decimal places (188.2990476) —
 * more than the 6 the database keeps, which the API rejects. It is only an estimate of the execution price, so
 * 4 places are plenty: 188.2990476 → "188.299". The quantity is not rounded: it has to match IB's exactly.
 */
const PREFILLED_PRICE_DECIMAL_PLACES = 4;

function prefilledPriceText(averageCost: number): string {
  return String(Number(averageCost.toFixed(PREFILLED_PRICE_DECIMAL_PLACES)));
}

/**
 * A holding with no trades yet is almost always being backfilled: the first entry is buying the whole position,
 * so the form starts as that, leaving just the date.
 */
function isBackfillOf(holding: Holding, tradeBeingEdited: Trade | null): boolean {
  return tradeBeingEdited === null && holding.trades.length === 0 && holding.position > 0;
}

function initialFormValues(holding: Holding, tradeBeingEdited: Trade | null, todayIsoDate: string): TradeFormValues {
  if (tradeBeingEdited !== null) {
    return {
      tradeDate: tradeBeingEdited.tradeDate,
      side: tradeBeingEdited.side,
      quantityText: String(tradeBeingEdited.quantity),
      priceText: tradeBeingEdited.price === null ? '' : String(tradeBeingEdited.price),
      note: tradeBeingEdited.note ?? '',
    };
  }
  if (isBackfillOf(holding, tradeBeingEdited)) {
    return {
      tradeDate: todayIsoDate,
      side: 'BUY',
      quantityText: String(holding.position),
      priceText: holding.averageCost === null ? '' : prefilledPriceText(holding.averageCost),
      note: '',
    };
  }
  return { tradeDate: todayIsoDate, side: 'BUY', quantityText: '', priceText: '', note: '' };
}

/** The same rules the API enforces, checked first so the common mistakes don't need a round trip. */
function problemWithForm(formValues: TradeFormValues, todayIsoDate: string): string | null {
  if (formValues.tradeDate === '') return 'Enter the trade date.';
  if (formValues.tradeDate > todayIsoDate) return 'The trade date cannot be in the future.';
  if (formValues.quantityText.trim() === '' || !(Number(formValues.quantityText) > 0)) {
    return 'Enter a quantity greater than 0.';
  }
  if (formValues.priceText.trim() !== '' && !(Number(formValues.priceText) >= 0)) {
    return 'The price cannot be negative.';
  }
  return null;
}

function toTradeRequest(formValues: TradeFormValues): TradeRequest {
  const trimmedNote = formValues.note.trim();
  return {
    tradeDate: formValues.tradeDate,
    side: formValues.side,
    quantity: Number(formValues.quantityText),
    price: formValues.priceText.trim() === '' ? null : Number(formValues.priceText),
    note: trimmedNote === '' ? null : trimmedNote,
  };
}

const INPUT_CLASS =
  'rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none';

function SideButton({
  side,
  selectedSide,
  onSelect,
}: {
  side: TradeSide;
  selectedSide: TradeSide;
  onSelect: (side: TradeSide) => void;
}) {
  const isSelected = side === selectedSide;
  const selectedColorClass =
    side === 'BUY'
      ? 'border-emerald-500/60 bg-emerald-500/15 text-emerald-300'
      : 'border-rose-500/60 bg-rose-500/15 text-rose-300';
  return (
    <button
      type="button"
      aria-pressed={isSelected}
      onClick={() => onSelect(side)}
      className={`rounded-md border px-2 py-1 text-[11px] font-semibold transition-colors ${
        isSelected ? selectedColorClass : 'border-slate-700 text-slate-400 hover:text-slate-200'
      }`}
    >
      {side}
    </button>
  );
}

/**
 * Adds a trade to `holding`, or corrects `tradeBeingEdited` when one is given. The API is the authority on what is
 * valid; its message is shown as it is. `onSaved` runs after a successful save and reloads the portfolio.
 */
export function TradeForm({
  holding,
  tradeBeingEdited,
  onSaved,
  onCancelEditing,
}: {
  holding: Holding;
  tradeBeingEdited: Trade | null;
  onSaved: () => Promise<void>;
  onCancelEditing: () => void;
}) {
  const todayIsoDate = localTodayIsoDate();
  const [formValues, setFormValues] = useState(() => initialFormValues(holding, tradeBeingEdited, todayIsoDate));
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isEditing = tradeBeingEdited !== null;
  const isBackfill = isBackfillOf(holding, tradeBeingEdited);

  const updateFormValue = <Field extends keyof TradeFormValues>(field: Field, value: TradeFormValues[Field]) =>
    setFormValues((currentValues) => ({ ...currentValues, [field]: value }));

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
      const tradeRequest = toTradeRequest(formValues);
      if (tradeBeingEdited === null) {
        await addTrade(holding.id, tradeRequest);
      } else {
        await changeTrade(tradeBeingEdited.id, tradeRequest);
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
        {isEditing ? `Edit the trade of ${tradeBeingEdited.tradeDate}` : 'Add a trade'}
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
          Date
          <input
            type="date"
            required
            max={todayIsoDate}
            value={formValues.tradeDate}
            onChange={(event) => updateFormValue('tradeDate', event.target.value)}
            className={INPUT_CLASS}
          />
        </label>

        <div className="flex gap-1" role="group" aria-label="Buy or sell">
          <SideButton side="BUY" selectedSide={formValues.side} onSelect={(side) => updateFormValue('side', side)} />
          <SideButton side="SELL" selectedSide={formValues.side} onSelect={(side) => updateFormValue('side', side)} />
        </div>

        <label className="flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
          Quantity
          <input
            type="number"
            step="any"
            min="0"
            required
            value={formValues.quantityText}
            onChange={(event) => updateFormValue('quantityText', event.target.value)}
            className={`${INPUT_CLASS} w-28`}
          />
        </label>

        <label className="flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
          Price
          <input
            type="number"
            step="any"
            min="0"
            placeholder="optional"
            value={formValues.priceText}
            onChange={(event) => updateFormValue('priceText', event.target.value)}
            className={`${INPUT_CLASS} w-28`}
          />
        </label>

        <label className="flex min-w-48 flex-1 flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
          Note
          <input
            type="text"
            maxLength={NOTE_MAX_LENGTH}
            placeholder="Why? (optional)"
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
          {isSaving ? 'Saving…' : isEditing ? 'Save changes' : 'Add trade'}
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

      {isBackfill && (
        <p className="text-[11px] text-slate-500">
          Prefilled from IB: the whole position at IB's average cost, which includes commissions — set the date,
          and adjust the price if you know the execution price.
        </p>
      )}
      {errorMessage && <p className="text-[11px] text-rose-400">{errorMessage}</p>}
    </form>
  );
}
