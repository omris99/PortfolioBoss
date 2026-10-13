import { useState, type ReactNode } from 'react';
import { formatDateTime, formatMoney, formatPercent, formatQuantity, formatSignedMoney, profitLossColorClass } from '../../lib/format';
import { INPUT_CLASS } from '../../lib/formInput';
import { NUMBER_INPUT_CLASS } from '../../lib/styles';
import type { Holding } from '../../types/portfolio';
import { Field, FormError } from '../FormFields';
import type { WholeAccount } from '../graphs/portfolioWeights';
import { WarnMark } from '../WarnMark';
import {
  holdingInAccountOf,
  percentOfAccountOf,
  targetPercentOf,
  weightPlanOf,
  type HoldingInAccount,
  type WeightPlan,
} from './targetWeight';

/** "Now 21.5% of the whole account · 21,520.00 of 100,000.00". */
function CurrentWeight({ holdingInAccount }: { holdingInAccount: HoldingInAccount }) {
  const { quantity, price, wholeAccount } = holdingInAccount;
  return (
    <p className="mb-4 flex items-baseline gap-2">
      <span className="text-label text-muted">Now</span>
      <span className="text-figure">{formatPercent(percentOfAccountOf(holdingInAccount))}</span>
      <span className="text-muted">
        of the whole account · {formatMoney(quantity * price)} of {formatMoney(wholeAccount.value)}
      </span>
    </p>
  );
}

/** A line of the result: its name on the left, the figures beside it. */
function PlanRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[136px_minmax(0,1fr)] items-baseline gap-x-3 py-1">
      <span className="text-label text-muted">{label}</span>
      <span>{children}</span>
    </div>
  );
}

/** The cash against what a purchase needs: said plainly when it covers it, as a warning when it doesn't. */
function CashCoverage({ cash, isCoveredByCash }: { cash: number; isCoveredByCash: boolean }) {
  if (isCoveredByCash) return <>{formatMoney(cash)}: enough</>;
  return (
    <span className="inline-flex items-center gap-1.5 text-warn-real">
      <WarnMark level="real" />
      {formatMoney(cash)}: less than the purchase and its commission
    </span>
  );
}

/** "Sell 31 shares ≈ 6,671.20", then what the account looks like after it. */
function TradePlan({ plan, cash }: { plan: Exclude<WeightPlan, { action: 'none' }>; cash: number }) {
  const isSale = plan.action === 'sell';
  return (
    <div className="mt-4 rounded-lg border px-4 py-3">
      <div className="mb-2 flex items-baseline gap-3 border-b pb-2">
        <span className="text-section-title">
          {isSale ? 'Sell' : 'Buy'} {plan.shareCount === 1 ? '1 share' : `${formatQuantity(plan.shareCount)} shares`}
        </span>
        <span className="ml-auto text-figure">≈ {formatMoney(plan.amount)}</span>
      </div>
      <PlanRow label={isSale ? 'After the sale' : 'After the purchase'}>
        {formatQuantity(plan.quantityAfter)} shares · {formatPercent(plan.percentAfter)} of the whole account
      </PlanRow>
      <PlanRow label="Commission">≈ {formatMoney(plan.commission)}</PlanRow>
      {plan.action === 'sell' && (
        <PlanRow label="Realized P&L">
          <span className={profitLossColorClass(plan.realizedPnl)}>≈ {formatSignedMoney(plan.realizedPnl)}</span>
          <span className="text-muted"> at IB’s average cost, the commission off</span>
        </PlanRow>
      )}
      {plan.action === 'buy' && (
        <PlanRow label="Cash">
          <CashCoverage cash={cash} isCoveredByCash={plan.isCoveredByCash} />
        </PlanRow>
      )}
    </div>
  );
}

/** What the target typed in comes to — or what to type, or why it can't be used. */
function PlanResult({ holdingInAccount, targetText }: { holdingInAccount: HoldingInAccount; targetText: string }) {
  const target = targetPercentOf(targetText);
  if (target === null) {
    return (
      <p className="mt-4 rounded-lg border border-dashed px-4 py-3 text-muted">
        Type a target weight to see how many shares to sell or buy.
      </p>
    );
  }
  if (typeof target !== 'number') return <FormError>{target.reason}</FormError>;

  const plan = weightPlanOf(holdingInAccount, target);
  if (plan.action === 'none') {
    return (
      <p className="mt-4 rounded-lg border px-4 py-3">
        Already at the target: no whole share brings it closer to {formatPercent(target)}.
      </p>
    );
  }
  return <TradePlan plan={plan} cash={holdingInAccount.wholeAccount.cash} />;
}

/**
 * The drawer's Weight tab: how many shares to sell or buy for the holding to weigh a target percent of the whole account
 * (the stocks plus the cash, as in the Overview's graphs). A calculation only: nothing reaches IB and nothing is saved.
 * `syncedAt` is the last sync's time, which the price is from.
 */
export function WeightCalculatorPanel({
  holding,
  wholeAccount,
  syncedAt,
}: {
  holding: Holding;
  wholeAccount: WholeAccount | null;
  syncedAt: string;
}) {
  const [targetText, setTargetText] = useState('');
  const holdingInAccount = holdingInAccountOf(holding, wholeAccount);
  if ('reason' in holdingInAccount) return <p className="py-6 text-muted">{holdingInAccount.reason}</p>;

  return (
    <div>
      <CurrentWeight holdingInAccount={holdingInAccount} />
      <Field label="Target weight" hint="Of the whole account: the stocks plus the cash.">
        <span className="flex items-center gap-2">
          <input
            type="text"
            inputMode="decimal"
            value={targetText}
            onChange={(event) => setTargetText(event.target.value)}
            className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-24`}
          />
          <span className="text-muted">%</span>
        </span>
      </Field>
      <PlanResult holdingInAccount={holdingInAccount} targetText={targetText} />
      <p className="mt-4 text-caption text-muted">
        At IB’s last price, {formatMoney(holdingInAccount.price)}, from the sync of {formatDateTime(syncedAt)}. A
        calculation only: nothing is sold or bought.
      </p>
    </div>
  );
}
