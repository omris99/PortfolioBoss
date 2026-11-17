import { useState } from 'react';
import * as Popover from '@radix-ui/react-popover';
import { formatDayCount } from '../../lib/format';
import { PRIMARY_BUTTON_CLASS, SECONDARY_BUTTON_CLASS, SMALL_SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { SettlementDay } from '../../types/taxes';

/**
 * Confirm the whole month, on a month's heading: every day of the month still waiting, at the amounts PortfolioBoss
 * calculated, in one request — stored together or not at all. It asks once first: "Confirm 2 days of October 2026 at
 * the amounts PortfolioBoss calculated?"
 */
export function ConfirmWholeMonthButton({
  monthName,
  settlementDaysToConfirm,
  isConfirming,
  onConfirmAsCalculated,
}: {
  monthName: string;
  settlementDaysToConfirm: SettlementDay[];
  isConfirming: boolean;
  onConfirmAsCalculated: (settlementDays: SettlementDay[]) => void;
}) {
  const [isAsking, setIsAsking] = useState(false);

  const confirmEveryDay = () => {
    setIsAsking(false);
    onConfirmAsCalculated(settlementDaysToConfirm);
  };

  return (
    <Popover.Root open={isAsking} onOpenChange={setIsAsking}>
      <Popover.Trigger disabled={isConfirming} className={SMALL_SECONDARY_BUTTON_CLASS}>
        Confirm the whole month
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="end"
          sideOffset={6}
          aria-label="Confirm the whole month"
          className="z-30 w-[320px] rounded-md border bg-surface-raised px-4 py-3 text-[13px] leading-[18px] text-ink shadow-raised"
        >
          <p className="text-body font-semibold">
            Confirm {formatDayCount(settlementDaysToConfirm.length)} of {monthName} at the amounts PortfolioBoss
            calculated?
          </p>
          <div className="mt-3 flex gap-2">
            <button type="button" onClick={confirmEveryDay} className={PRIMARY_BUTTON_CLASS}>
              Confirm
            </button>
            <Popover.Close className={SECONDARY_BUTTON_CLASS}>Cancel</Popover.Close>
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
