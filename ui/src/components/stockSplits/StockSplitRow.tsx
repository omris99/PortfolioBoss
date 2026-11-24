import { INNER_CELL_CLASS } from '../../lib/styles';
import type { StockSplit } from '../../types/portfolio';
import { ratioInIbsWords, whatTheSplitMeans } from './stockSplitTexts';

/**
 * A split in a holding's trades, at its date (STOCK_SPLITS_TODO.md, decision 8): grey — information, not a warning —
 * and without ✏️ or 🗑, since it comes from IB's report. `columnCount` is the table's: the line spans it after the date.
 */
export function StockSplitRow({ stockSplit, columnCount }: { stockSplit: StockSplit; columnCount: number }) {
  return (
    <tr title={whatTheSplitMeans(stockSplit)} className="bg-subtle">
      <td className={INNER_CELL_CLASS}>{stockSplit.splitDay}</td>
      <td colSpan={columnCount - 1} className={INNER_CELL_CLASS}>
        <span className="font-medium">Split {ratioInIbsWords(stockSplit)}</span>
        <span className="text-muted"> · from IB’s report</span>
      </td>
    </tr>
  );
}
