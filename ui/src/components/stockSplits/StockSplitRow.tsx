import { formatDate } from '../../lib/format';
import { INNER_CELL_CLASS } from '../../lib/styles';
import type { StockSplit } from '../../types/portfolio';
import { Hint } from '../Hint';
import { ratioInIbsWords, whatTheSplitMeans } from './stockSplitTexts';

/**
 * A split in a holding's trades, at its date (STOCK_SPLITS_TODO.md, decision 8): grey — information, not a warning —
 * and without ✏️ or 🗑, since it comes from IB's report; what it means in its hint. `columnCount` is the table's: the line
 * spans it after the date.
 */
export function StockSplitRow({ stockSplit, columnCount }: { stockSplit: StockSplit; columnCount: number }) {
  return (
    <tr className="bg-subtle">
      <td className={INNER_CELL_CLASS}>{formatDate(stockSplit.splitDay)}</td>
      <td colSpan={columnCount - 1} className={INNER_CELL_CLASS}>
        <Hint text={whatTheSplitMeans(stockSplit)} align="start">
          <span>
            <span className="font-medium">Split {ratioInIbsWords(stockSplit)}</span>
            <span className="text-muted"> · from IB’s report</span>
          </span>
        </Hint>
      </td>
    </tr>
  );
}
