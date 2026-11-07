import { Wallet } from 'lucide-react';
import { formatMoney } from '../../lib/format';
import { CARD_CLASS, EXPLAINED_CLASS, SMALL_SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { WarnMark } from '../WarnMark';
import type { PlanTotal } from './averagingPlan';

function Separator() {
  return (
    <span aria-hidden="true" className="text-faint">
      ·
    </span>
  );
}

function purchaseCountText(purchaseCount: number): string {
  return purchaseCount === 1 ? '1 purchase' : `${purchaseCount} purchases`;
}

/** "3 purchases · 6,325.95 + commissions 15.00 = 6,340.95", and "Cash left 6,066.85" while the cash covers it. */
function PlanTotalText({ planTotal, isShort }: { planTotal: PlanTotal; isShort: boolean }) {
  return (
    <>
      <Separator />
      <span>{purchaseCountText(planTotal.purchaseCount)}</span>
      <Separator />
      <span>
        {formatMoney(planTotal.amount)} + commissions {formatMoney(planTotal.commissions)} ={' '}
        <b className="font-semibold">{formatMoney(planTotal.total)}</b>
      </span>
      {!isShort && (
        <>
          <Separator />
          <span>
            Cash left <b className="text-[16px] font-semibold">{formatMoney(planTotal.cashLeft)}</b>
          </span>
        </>
      )}
    </>
  );
}

/**
 * The Averaging calculator's cash card: the tab's cash — IB's on Portfolio, the investor's own on their tab —, and every
 * purchase typed on the tab added up against it, the one thing the purchases share. When the cash can't pay for them, the
 * real warning says by how much; nothing is blocked. It stays in view at the top of the screen down a long list. Clear
 * empties every field on this tab only.
 */
export function AveragingCashCard({
  tab,
  cash,
  planTotal,
  onClear,
}: {
  tab: InvestmentsAndCash;
  /** The tab's cash: below zero when stocks were bought with borrowed money — every purchase is short then. */
  cash: number;
  planTotal: PlanTotal;
  onClear: () => void;
}) {
  const hasPurchases = planTotal.purchaseCount > 0;
  const isShort = planTotal.cashLeft < 0;
  return (
    <section aria-label="Cash and plan" className={`${CARD_CLASS} sticky top-2 z-10 px-5 py-3 shadow-raised`}>
      <div className="flex min-h-[32px] flex-wrap items-center gap-2">
        <Wallet size={16} aria-hidden="true" className="shrink-0 text-muted" />
        <span className="inline-flex items-baseline gap-1.5 text-muted">
          <span title={tab.cashHint} className={EXPLAINED_CLASS}>
            Cash
          </span>
          <b className="text-[16px] font-semibold text-ink">{formatMoney(cash)}</b>
        </span>
        {hasPurchases ? (
          <>
            <PlanTotalText planTotal={planTotal} isShort={isShort} />
            <button type="button" onClick={onClear} className={`${SMALL_SECONDARY_BUTTON_CLASS} ml-auto`}>
              Clear
            </button>
          </>
        ) : (
          <span className="ml-auto text-caption text-faint">
            Type a target weight or an amount on a row to add it here.
          </span>
        )}
      </div>
      {hasPurchases && isShort && (
        <p role="status" className="mt-2 flex items-center gap-2 border-t pt-2 text-[13px] font-medium text-warn-real">
          <WarnMark level="real" />
          <span>
            {formatMoney(-planTotal.cashLeft)} short: the purchases and their commissions come to{' '}
            {formatMoney(planTotal.total)}, more than the cash of {formatMoney(cash)}
          </span>
        </p>
      )}
    </section>
  );
}
