import { Check } from 'lucide-react';
import { formatDayCount } from '../../lib/format';
import type { SettlementDay } from '../../types/taxes';
import { ConfirmWholeMonthButton } from './ConfirmWholeMonthButton';
import { monthHeadingText, monthName } from './taxTexts';
import { checkingOffCountsOf, settlementDaysToConfirmAsCalculated, type MonthWithItsSettlementDays } from './taxYears';

/** On Portfolio, at the heading's right: "2 days to confirm" and the button, or a quiet "Checked off". */
function MonthCheckingOff({
  month,
  isConfirming,
  onConfirmAsCalculated,
}: {
  month: MonthWithItsSettlementDays;
  isConfirming: boolean;
  onConfirmAsCalculated: (settlementDays: SettlementDay[]) => void;
}) {
  const settlementDaysToConfirm = settlementDaysToConfirmAsCalculated(month);
  if (settlementDaysToConfirm.length > 0) {
    return (
      <span className="flex items-center gap-3">
        <span className="text-caption text-muted">{formatDayCount(settlementDaysToConfirm.length)} to confirm</span>
        <ConfirmWholeMonthButton
          monthName={monthName(month.monthlySettlement.month)}
          settlementDaysToConfirm={settlementDaysToConfirm}
          isConfirming={isConfirming}
          onConfirmAsCalculated={onConfirmAsCalculated}
        />
      </span>
    );
  }
  const { confirmedDays, daysNeedingAConfirmation } = checkingOffCountsOf(month.settlementDays);
  const isCheckedOff = daysNeedingAConfirmation > 0 && confirmedDays === daysNeedingAConfirmation;
  if (!isCheckedOff) return null;
  return (
    <span className="flex items-center gap-1 text-caption text-muted">
      <Check size={14} />
      Checked off
    </span>
  );
}

/**
 * A month's heading row in the year's one table: "June 2026 · tax ₪1,279.40 → paid", on the page's grey; on Portfolio its
 * checking off at the right.
 */
export function MonthHeadingRow({
  month,
  columnCount,
  isPortfolioTab,
  isConfirming,
  onConfirmAsCalculated,
}: {
  month: MonthWithItsSettlementDays;
  columnCount: number;
  isPortfolioTab: boolean;
  isConfirming: boolean;
  onConfirmAsCalculated: (settlementDays: SettlementDay[]) => void;
}) {
  return (
    <tr className="border-b">
      <td colSpan={columnCount} className="h-11 bg-page px-5">
        <div className="flex items-center justify-between gap-4">
          <p>
            <span className="text-section-title">{monthName(month.monthlySettlement.month)}</span>
            <span className="text-body text-muted"> · {monthHeadingText(month.monthlySettlement)}</span>
          </p>
          {isPortfolioTab && (
            <MonthCheckingOff month={month} isConfirming={isConfirming} onConfirmAsCalculated={onConfirmAsCalculated} />
          )}
        </div>
      </td>
    </tr>
  );
}
