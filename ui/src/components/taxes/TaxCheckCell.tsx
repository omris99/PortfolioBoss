import type { SyntheticEvent } from 'react';
import { Check, Clock } from 'lucide-react';
import { formatDateTime, formatShekels } from '../../lib/format';
import { SMALL_SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { SettlementDay } from '../../types/taxes';
import { HelpOnHover } from '../HelpOnHover';
import { BrokerRecordedAmountForm } from './BrokerRecordedAmountForm';
import { isEverySaleCalculated, isSameAmount, waitingForARateText } from './taxTexts';

/**
 * The ✓'s explanation: when it was confirmed and the note — and, when the broker recorded another amount than
 * PortfolioBoss calculated, that amount too, without a color: the confirmed one is final (decision 7), this one is only
 * there to see.
 */
function ConfirmedDayExplanation({ settlementDay }: { settlementDay: SettlementDay }) {
  const confirmation = settlementDay.confirmation;
  if (confirmation === null) return null;
  const note = confirmation.note === null ? '' : ` · ${confirmation.note}`;
  const isCalculatedAmountAnother =
    isEverySaleCalculated(settlementDay) && !isSameAmount(confirmation.taxAmount, settlementDay.computedTaxAmount);
  return (
    <>
      <p>
        Confirmed {formatDateTime(confirmation.confirmedAt)}
        {note}
      </p>
      {isCalculatedAmountAnother && (
        <p className="text-muted">PortfolioBoss calculated {formatShekels(settlementDay.computedTaxAmount)}</p>
      )}
    </>
  );
}

/** The day's row opens on a click: what is clicked in this cell, its popovers included, stays here. */
function keepFromTheDayRow(event: SyntheticEvent) {
  event.stopPropagation();
}

/**
 * The Check column of one of the account's settlement days — Portfolio's only: what the broker recorded, and the
 * controls to check it off. A day is waiting or confirmed (decision 7):
 * - calculated, waiting: Confirm (the amount PortfolioBoss calculated, as the broker recorded it) and the pencil;
 * - confirmed: a quiet ✓ — when and the note in its explanation — and the pencil;
 * - waiting for a rate: the clock, with why;
 * - can't be calculated: the pencil, outlined — the way in;
 * - a 0, or a sale beyond its buys: nothing.
 */
export function TaxCheckCell({
  settlementDay,
  isConfirming,
  onConfirmAsCalculated,
  onConfirmationChanged,
}: {
  settlementDay: SettlementDay;
  isConfirming: boolean;
  onConfirmAsCalculated: (settlementDays: SettlementDay[]) => void;
  onConfirmationChanged: () => Promise<void>;
}) {
  const pencil = (
    <BrokerRecordedAmountForm settlementDay={settlementDay} onConfirmationChanged={onConfirmationChanged} />
  );
  const cellContent = () => {
    switch (settlementDay.status) {
      case 'CALCULATED':
        return (
          <>
            <button
              type="button"
              disabled={isConfirming}
              onClick={() => onConfirmAsCalculated([settlementDay])}
              className={SMALL_SECONDARY_BUTTON_CLASS}
            >
              Confirm
            </button>
            {pencil}
          </>
        );
      case 'CONFIRMED':
        return (
          <>
            <HelpOnHover label="Confirmed" icon={<Check size={16} className="text-muted" />}>
              <ConfirmedDayExplanation settlementDay={settlementDay} />
            </HelpOnHover>
            {pencil}
          </>
        );
      case 'WAITING_FOR_A_RATE':
        return (
          <HelpOnHover label="Not calculated yet" icon={<Clock size={16} />}>
            {waitingForARateText(settlementDay.settlementDay)}
          </HelpOnHover>
        );
      case 'CANNOT_BE_CALCULATED':
        return (
          <BrokerRecordedAmountForm
            settlementDay={settlementDay}
            isTheWayIn
            onConfirmationChanged={onConfirmationChanged}
          />
        );
      case 'CALCULATED_TO_ZERO':
      case 'SOLD_MORE_THAN_BOUGHT':
        return null;
    }
  };
  return (
    <span onClick={keepFromTheDayRow} onKeyDown={keepFromTheDayRow} className="flex items-center gap-2">
      {cellContent()}
    </span>
  );
}
