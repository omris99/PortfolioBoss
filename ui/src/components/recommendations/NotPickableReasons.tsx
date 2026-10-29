import { Fragment } from 'react';
import { formatSignedPercentOneDecimal } from '../../lib/format';
import type { HotStock, NotPickableReason } from '../../types/recommendations';
import { RiskSigns } from './RiskSignTag';

/**
 * Why the recommendation can't pick a stock, in words, each with the figure behind it — the API's reasons, in its
 * order (RECOMMENDATIONS_TODO.md, decisions 5 and 14). The numbers in the words are copies of the API's, for the words
 * only.
 */

function ReasonText({ reason, stock }: { reason: NotPickableReason; stock: HotStock }) {
  switch (reason) {
    case 'NO_ANALYST_TARGET':
      return <span className="text-muted">no analysts’ target: fewer than 3 firms</span>;
    case 'TARGET_TOO_CLOSE':
      return (
        <span className="text-muted">
          analysts’ target {formatSignedPercentOneDecimal(stock.analystTarget.upsidePercent)}; a pick needs +15%
        </span>
      );
    case 'NO_COMPANY_FACTS':
      return <span className="text-muted">not checked: no figures from Finnhub</span>;
    case 'RISK_SIGNS_TOO_HEAVY':
      return (
        <>
          <RiskSigns stock={stock} showFigures />
          <span className="text-muted">weigh {stock.riskWeight}; a pick’s weigh 2 at most</span>
        </>
      );
  }
}

/** Every reason against the stock, "·" between them; nothing when it can be picked. */
export function NotPickableReasons({ stock }: { stock: HotStock }) {
  return (
    <>
      {stock.reasonsNotPickable.map((reason, index) => (
        <Fragment key={reason}>
          {index > 0 && (
            <span aria-hidden="true" className="text-faint">
              ·
            </span>
          )}
          <ReasonText reason={reason} stock={stock} />
        </Fragment>
      ))}
    </>
  );
}
