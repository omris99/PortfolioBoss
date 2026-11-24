import { INNER_LINE_UNDER_A_ROW_CELL_CLASS } from '../../lib/styles';
import type { Trade } from '../../types/portfolio';
import { tradeBeforeTheSplitText } from './stockSplitTexts';

/**
 * The line under a trade a split translated: the row above shows it as it counts after the split, this line what was
 * typed — "= 10 at 400.00 before the split" —, under the trade's side. `columnCount` is the table's.
 */
export function TradeBeforeTheSplitRow({ trade, columnCount }: { trade: Trade; columnCount: number }) {
  return (
    <tr>
      <td aria-hidden="true" className={INNER_LINE_UNDER_A_ROW_CELL_CLASS} />
      <td colSpan={columnCount - 1} className={`${INNER_LINE_UNDER_A_ROW_CELL_CLASS} text-caption text-muted`}>
        = {tradeBeforeTheSplitText(trade)}
      </td>
    </tr>
  );
}
