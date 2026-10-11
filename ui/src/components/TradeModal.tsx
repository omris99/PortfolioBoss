import { useState } from 'react';
import { useSaveAction } from '../hooks/useSaveAction';
import { addManualPositionTrade, addTrade, changeTrade } from '../lib/apiClient';
import { formatMoney, formatQuantity } from '../lib/format';
import {
  INPUT_CLASS,
  isBlank,
  isIsoDate,
  isPositiveWholeNumber,
  localTodayIsoDate,
  numberOrNull,
  trimmedOrNull,
} from '../lib/formInput';
import { NUMBER_INPUT_CLASS } from '../lib/styles';
import type { Holding, Trade, TradeRequest, TradeSide } from '../types/portfolio';
import { DateInput, Field, InfoBand, Toggle } from './FormFields';
import { InvestorSelect } from './InvestorSelect';
import { accountOwnerOf, hasSeveralInvestors, useInvestors } from './InvestorsContext';
import { Modal } from './Modal';

/**
 * Whose trades a form or panel works on: a holding's, or a manual position's — one PortfolioBoss never saw as a
 * holding. Both are corrected and deleted the same way; only adding goes to a different endpoint.
 */
export type TradeOwner =
  | { kind: 'holding'; holding: Holding }
  | { kind: 'manualPosition'; manualPositionId: number; symbol: string; trades: Trade[] };

export function symbolOf(owner: TradeOwner): string {
  return owner.kind === 'holding' ? owner.holding.symbol : owner.symbol;
}

const NOTE_MAX_LENGTH = 500;   // the trade.note column

const COMMISSION_HINT = 'Empty means the default: 1¢ a share, at least $5 an order. 0 is no commission.';

/** What the inputs hold, as typed: number inputs give text, and an empty one means "not entered". */
interface TradeFormValues {
  tradeDate: string;
  side: TradeSide;
  quantityText: string;
  priceText: string;
  commissionText: string;
  note: string;
  /** Whose trade it is: the account owner's unless another investor is picked. `null` only before the first load. */
  investorId: number | null;
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
export function backfilledHoldingOf(owner: TradeOwner, tradeBeingEdited: Trade | null): Holding | null {
  if (owner.kind !== 'holding' || tradeBeingEdited !== null) return null;
  const { holding } = owner;
  return holding.trades.length === 0 && holding.position > 0 ? holding : null;
}

function addTradeTo(owner: TradeOwner, tradeRequest: TradeRequest): Promise<void> {
  return owner.kind === 'holding'
    ? addTrade(owner.holding.id, tradeRequest)
    : addManualPositionTrade(owner.manualPositionId, tradeRequest);
}

/** A new trade starts as the account owner's; one being corrected keeps its own investor until another is picked. */
function initialFormValues(
  owner: TradeOwner,
  tradeBeingEdited: Trade | null,
  todayIsoDate: string,
  accountOwnerId: number | null,
): TradeFormValues {
  if (tradeBeingEdited !== null) {
    return {
      tradeDate: tradeBeingEdited.tradeDate,
      side: tradeBeingEdited.side,
      quantityText: String(tradeBeingEdited.quantity),
      priceText: tradeBeingEdited.price === null ? '' : String(tradeBeingEdited.price),
      // The commission stored — the default, if none was entered. Emptying it works the default out again.
      commissionText: String(tradeBeingEdited.commission),
      note: tradeBeingEdited.note ?? '',
      investorId: tradeBeingEdited.investorId,
    };
  }
  const backfilledHolding = backfilledHoldingOf(owner, tradeBeingEdited);
  return {
    tradeDate: todayIsoDate,
    side: 'BUY',
    quantityText: backfilledHolding === null ? '' : String(backfilledHolding.position),
    priceText:
      backfilledHolding === null || backfilledHolding.averageCost === null
        ? ''
        : prefilledPriceText(backfilledHolding.averageCost),
    commissionText: '',
    note: '',
    investorId: accountOwnerId,
  };
}

/** The same rules the API enforces, checked first so the common mistakes don't need a round trip. */
function problemWithForm(formValues: TradeFormValues, todayIsoDate: string): string | null {
  if (!isIsoDate(formValues.tradeDate)) return 'Enter the trade date as yyyy-MM-dd.';
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
    investorId: formValues.investorId,
  };
}

/** "No trades entered yet, so this starts as a BUY of the whole position (40) at IB's average cost, 412.30." */
function BackfillNote({ holding }: { holding: Holding }) {
  return (
    <InfoBand>
      No trades entered yet, so this starts as a BUY of the whole position (
      <b className="font-semibold">{formatQuantity(holding.position)}</b>) at IB’s average cost,{' '}
      <b className="font-semibold">{formatMoney(holding.averageCost)}</b>, which includes commissions. Set the date it
      was bought, and the price if you know the execution price.
    </InfoBand>
  );
}

/**
 * Adds a trade to `owner`, or corrects `tradeBeingEdited` when one is given. The API is the authority on what is
 * valid; its message is shown as it is. `onSaved` reloads the portfolio after a successful save, then the modal closes.
 */
export function TradeModal({
  owner,
  tradeBeingEdited,
  onSaved,
  onClose,
}: {
  owner: TradeOwner;
  tradeBeingEdited: Trade | null;
  onSaved: () => Promise<void>;
  onClose: () => void;
}) {
  const todayIsoDate = localTodayIsoDate();
  const investors = useInvestors();
  const [formValues, setFormValues] = useState(() =>
    initialFormValues(owner, tradeBeingEdited, todayIsoDate, accountOwnerOf(investors)?.id ?? null),
  );
  const { isSaving, errorMessage, setErrorMessage, runSave } = useSaveAction();

  const isEditing = tradeBeingEdited !== null;
  const backfilledHolding = backfilledHoldingOf(owner, tradeBeingEdited);

  const updateFormValue = <Field extends keyof TradeFormValues>(field: Field, value: TradeFormValues[Field]) =>
    setFormValues((currentValues) => ({ ...currentValues, [field]: value }));

  const handleSubmit = async () => {
    const problem = problemWithForm(formValues, todayIsoDate);
    if (problem !== null) {
      setErrorMessage(problem);
      return;
    }
    const tradeRequest = toTradeRequest(formValues);
    const isSaved = await runSave(async () => {
      if (tradeBeingEdited === null) {
        await addTradeTo(owner, tradeRequest);
      } else {
        await changeTrade(tradeBeingEdited.id, tradeRequest);
      }
      await onSaved();
    });
    if (isSaved) onClose();
  };

  return (
    <Modal
      title={isEditing ? 'Edit trade' : 'Add trade'}
      subtitle={symbolOf(owner)}
      errorMessage={errorMessage}
      isSaving={isSaving}
      saveLabel={isEditing ? 'Save changes' : 'Save'}
      onSubmit={() => void handleSubmit()}
      onClose={onClose}
    >
      {backfilledHolding !== null && <BackfillNote holding={backfilledHolding} />}
      <div className="grid grid-cols-2 gap-4">
        {hasSeveralInvestors(investors) && (
          <Field label="Investor" span={2}>
            <InvestorSelect
              investorId={formValues.investorId}
              onChange={(investorId) => updateFormValue('investorId', investorId)}
            />
          </Field>
        )}
        <Field label="Date" hint="Not in the future.">
          <DateInput
            value={formValues.tradeDate}
            max={todayIsoDate}
            onChange={(tradeDate) => updateFormValue('tradeDate', tradeDate)}
          />
        </Field>
        <Field label="Side" isGroup>
          <Toggle
            label="Buy or sell"
            options={['BUY', 'SELL'] as TradeSide[]}
            value={formValues.side}
            onChange={(side) => updateFormValue('side', side)}
          />
        </Field>
        <Field label="Quantity" hint="Whole shares, more than 0.">
          <input
            type="text"
            inputMode="numeric"
            value={formValues.quantityText}
            onChange={(event) => updateFormValue('quantityText', event.target.value)}
            className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-full`}
          />
        </Field>
        <Field label="Price" optional>
          <input
            type="text"
            inputMode="decimal"
            value={formValues.priceText}
            onChange={(event) => updateFormValue('priceText', event.target.value)}
            className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-full`}
          />
        </Field>
        <Field label="Commission" optional span={2} hint={COMMISSION_HINT}>
          <input
            type="text"
            inputMode="decimal"
            placeholder="Default"
            value={formValues.commissionText}
            onChange={(event) => updateFormValue('commissionText', event.target.value)}
            className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-full max-w-[200px]`}
          />
        </Field>
        <Field label="Note" optional span={2}>
          <input
            type="text"
            maxLength={NOTE_MAX_LENGTH}
            placeholder="Why? (optional)"
            value={formValues.note}
            onChange={(event) => updateFormValue('note', event.target.value)}
            className={`${INPUT_CLASS} w-full`}
          />
        </Field>
      </div>
    </Modal>
  );
}
