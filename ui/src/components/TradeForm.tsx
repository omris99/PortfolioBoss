import { useState, type FormEvent } from 'react';
import { addManualPositionTrade, addTrade, changeTrade, errorMessageOf } from '../lib/apiClient';
import { INPUT_CLASS, isBlank, isPositiveWholeNumber, localTodayIsoDate, numberOrNull, trimmedOrNull } from '../lib/formInput';
import type { Holding, Trade, TradeRequest, TradeSide } from '../types/portfolio';

/**
 * Whose trades a form or panel works on: a holding's, or a manual position's — one PortfolioBoss never saw as a
 * holding. Both are corrected and deleted the same way; only adding goes to a different endpoint.
 */
export type TradeOwner =
  | { kind: 'holding'; holding: Holding }
  | { kind: 'manualPosition'; manualPositionId: number; symbol: string; trades: Trade[] };

const NOTE_MAX_LENGTH = 500;   // the trade.note column

/** The API's rule (`Utils.calculateOrderCommission`), said where the field is. */
const COMMISSION_HINT =
  'Leave empty for the default: 1 cent a share, at least $5 — worked out again from the quantity on every save. ' +
  'Enter 0 for no commission.';

/** What the inputs hold, as typed: number inputs give text, and an empty one means "not entered". */
interface TradeFormValues {
  tradeDate: string;
  side: TradeSide;
  quantityText: string;
  priceText: string;
  commissionText: string;
  note: string;
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
 * The holding this form backfills, if any. A holding with no trades yet is almost always being backfilled: the first
 * entry is buying the whole position, so the form starts as that, leaving just the date. Never a manual position: IB
 * knows nothing about it.
 */
function backfilledHoldingOf(owner: TradeOwner, tradeBeingEdited: Trade | null): Holding | null {
  if (owner.kind !== 'holding' || tradeBeingEdited !== null) return null;
  const { holding } = owner;
  return holding.trades.length === 0 && holding.position > 0 ? holding : null;
}

function addTradeTo(owner: TradeOwner, tradeRequest: TradeRequest): Promise<void> {
  return owner.kind === 'holding'
    ? addTrade(owner.holding.id, tradeRequest)
    : addManualPositionTrade(owner.manualPositionId, tradeRequest);
}

function initialFormValues(owner: TradeOwner, tradeBeingEdited: Trade | null, todayIsoDate: string): TradeFormValues {
  if (tradeBeingEdited !== null) {
    return {
      tradeDate: tradeBeingEdited.tradeDate,
      side: tradeBeingEdited.side,
      quantityText: String(tradeBeingEdited.quantity),
      priceText: tradeBeingEdited.price === null ? '' : String(tradeBeingEdited.price),
      // The commission stored — the default, if none was entered. Emptying it works the default out again.
      commissionText: String(tradeBeingEdited.commission),
      note: tradeBeingEdited.note ?? '',
    };
  }
  const backfilledHolding = backfilledHoldingOf(owner, tradeBeingEdited);
  if (backfilledHolding !== null) {
    return {
      tradeDate: todayIsoDate,
      side: 'BUY',
      quantityText: String(backfilledHolding.position),
      priceText: backfilledHolding.averageCost === null ? '' : prefilledPriceText(backfilledHolding.averageCost),
      commissionText: '',
      note: '',
    };
  }
  return { tradeDate: todayIsoDate, side: 'BUY', quantityText: '', priceText: '', commissionText: '', note: '' };
}

/** The same rules the API enforces, checked first so the common mistakes don't need a round trip. */
function problemWithForm(formValues: TradeFormValues, todayIsoDate: string): string | null {
  if (formValues.tradeDate === '') return 'Enter the trade date.';
  if (formValues.tradeDate > todayIsoDate) return 'The trade date cannot be in the future.';
  if (!isPositiveWholeNumber(formValues.quantityText)) return 'Enter a whole quantity greater than 0.';
  if (!isBlank(formValues.priceText) && !(Number(formValues.priceText) >= 0)) {
    return 'The price cannot be negative.';
  }
  if (!isBlank(formValues.commissionText) && !(Number(formValues.commissionText) >= 0)) {
    return 'The commission cannot be negative.';
  }
  return null;
}

function toTradeRequest(formValues: TradeFormValues): TradeRequest {
  return {
    tradeDate: formValues.tradeDate,
    side: formValues.side,
    quantity: Number(formValues.quantityText),
    price: numberOrNull(formValues.priceText),
    note: trimmedOrNull(formValues.note),
    commission: numberOrNull(formValues.commissionText),
  };
}

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
 * Adds a trade to `owner`, or corrects `tradeBeingEdited` when one is given. The API is the authority on what is
 * valid; its message is shown as it is. `onSaved` runs after a successful save and reloads the portfolio.
 */
export function TradeForm({
  owner,
  tradeBeingEdited,
  onSaved,
  onCancelEditing,
}: {
  owner: TradeOwner;
  tradeBeingEdited: Trade | null;
  onSaved: () => Promise<void>;
  onCancelEditing: () => void;
}) {
  const todayIsoDate = localTodayIsoDate();
  const [formValues, setFormValues] = useState(() => initialFormValues(owner, tradeBeingEdited, todayIsoDate));
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const isEditing = tradeBeingEdited !== null;
  const isBackfill = backfilledHoldingOf(owner, tradeBeingEdited) !== null;

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
        await addTradeTo(owner, tradeRequest);
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
            step="1"
            min="1"
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

        <label
          title={COMMISSION_HINT}
          className="flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500"
        >
          Commission
          <input
            type="number"
            step="any"
            min="0"
            placeholder="default"
            value={formValues.commissionText}
            onChange={(event) => updateFormValue('commissionText', event.target.value)}
            className={`${INPUT_CLASS} w-24`}
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
