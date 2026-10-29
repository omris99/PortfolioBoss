import { useState, type ReactNode } from 'react';
import { Check } from 'lucide-react';
import { formatDateTime, formatMoney, formatPercent, formatQuantity, formatSignedMoney, profitLossColorClass } from '../../lib/format';
import { INPUT_CLASS } from '../../lib/formInput';
import { NUMBER_INPUT_CLASS } from '../../lib/styles';
import type { Holding } from '../../types/portfolio';
import { Field, FormError, InfoBand } from '../FormFields';
import { weightsTabOf, type InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { WeightsTabs } from '../graphs/WeightsTabs';
import { WarnMark } from '../WarnMark';
import {
  holdingInAccountOf,
  percentOfAccountOf,
  targetPercentOf,
  weightPlanOf,
  type CannotCalculate,
  type HoldingInAccount,
  type WeightPlan,
} from './targetWeight';

/** "1 share", "26 shares". */
export function shareCountText(shareCount: number): string {
  return shareCount === 1 ? '1 share' : `${formatQuantity(shareCount)} shares`;
}

/** "Now 21.5% of the whole account · 21,520.00 of 100,000.00" — of all of Shay's money, on Shay's tab. */
function CurrentWeight({ holdingInAccount, tab }: { holdingInAccount: HoldingInAccount; tab: InvestmentsAndCash }) {
  const { quantity, price, wholeAccount } = holdingInAccount;
  return (
    <p className="mb-4 text-muted">
      Now{' '}
      <span className="text-[16px] font-semibold text-ink">{formatPercent(percentOfAccountOf(holdingInAccount))}</span>{' '}
      of {tab.wholeName} ·{' '}
      <span className="text-ink">
        {formatMoney(quantity * price)} of {formatMoney(wholeAccount.value)}
      </span>
    </p>
  );
}

/** One line of the result, on a hairline: its name on the left, its figures on the right. */
export function PlanRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-t py-2">
      <dt className="shrink-0 text-muted">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}

/** A sale's realized P&L, and under it what it is worked out at. */
function RealizedPnl({ realizedPnl, tab }: { realizedPnl: number | null; tab: InvestmentsAndCash }) {
  return (
    <>
      <span className={profitLossColorClass(realizedPnl)}>≈ {formatSignedMoney(realizedPnl)}</span>
      <span className="block text-caption text-faint">at {tab.averageCostName}, the commission off</span>
    </>
  );
}

/** The cash against what a purchase needs: said plainly when it covers it, as a warning when it doesn't. */
export function CashCoverage({ cash, isCoveredByCash }: { cash: number; isCoveredByCash: boolean }) {
  if (isCoveredByCash) return <>{formatMoney(cash)}: enough</>;
  return (
    <span className="inline-flex items-start gap-1.5 font-medium text-warn-real">
      <span className="mt-0.5">
        <WarnMark level="real" />
      </span>
      {formatMoney(cash)}: less than the purchase and its commission
    </span>
  );
}

/** "Sell 31 shares ≈ 6,671.20", then what the account looks like after it. */
function TradePlan({
  plan,
  cash,
  tab,
}: {
  plan: Exclude<WeightPlan, { action: 'none' }>;
  cash: number;
  tab: InvestmentsAndCash;
}) {
  const isSale = plan.action === 'sell';
  return (
    <div className="mt-4 rounded-lg border px-4 py-3">
      <div className="flex items-baseline justify-between gap-4 pb-2">
        <span className="text-[16px] font-semibold leading-6">
          {isSale ? 'Sell' : 'Buy'} {shareCountText(plan.shareCount)}
        </span>
        <span className="text-[16px] font-medium">≈ {formatMoney(plan.amount)}</span>
      </div>
      <dl>
        <PlanRow label={isSale ? 'After the sale' : 'After the purchase'}>
          {shareCountText(plan.quantityAfter)} · {formatPercent(plan.percentAfter)} of {tab.wholeName}
        </PlanRow>
        <PlanRow label="Commission">≈ {formatMoney(plan.commission)}</PlanRow>
        {plan.action === 'sell' && (
          <PlanRow label="Realized P&L">
            <RealizedPnl realizedPnl={plan.realizedPnl} tab={tab} />
          </PlanRow>
        )}
        {plan.action === 'buy' && (
          <PlanRow label="Cash">
            <CashCoverage cash={cash} isCoveredByCash={plan.isCoveredByCash} />
          </PlanRow>
        )}
      </dl>
    </div>
  );
}

/** What the target typed in comes to — or what to type, or why it can't be used. */
function PlanResult({
  holdingInAccount,
  target,
  tab,
}: {
  holdingInAccount: HoldingInAccount;
  /** The percent typed in; `null` while nothing is typed. */
  target: number | CannotCalculate | null;
  tab: InvestmentsAndCash;
}) {
  if (target === null) {
    return (
      <p className="mt-4 rounded-lg border border-dashed border-strong px-4 py-5 text-center text-muted">
        Type a target weight to see how many shares to sell or buy.
      </p>
    );
  }
  if (typeof target !== 'number') return <FormError>{target.reason}</FormError>;

  const plan = weightPlanOf(holdingInAccount, target);
  if (plan.action === 'none') {
    return (
      <p className="mt-4 flex items-center gap-2 rounded-lg bg-subtle px-4 py-3">
        <Check size={16} className="shrink-0 text-muted" />
        Already at the target: no whole share brings it closer to {formatPercent(target)}.
      </p>
    );
  }
  return <TradePlan plan={plan} cash={holdingInAccount.wholeAccount.cash} tab={tab} />;
}

/**
 * The calculator on one tab: the weight now, the target field — its border red while what is typed can't be used —, and
 * what the target comes to.
 */
function WeightCalculator({
  holdingInAccount,
  tab,
  targetText,
  onTargetTextChange,
}: {
  holdingInAccount: HoldingInAccount;
  tab: InvestmentsAndCash;
  targetText: string;
  onTargetTextChange: (typedText: string) => void;
}) {
  const target = targetPercentOf(targetText);
  const isTargetUnusable = target !== null && typeof target !== 'number';
  return (
    <>
      <CurrentWeight holdingInAccount={holdingInAccount} tab={tab} />
      <div className="max-w-[360px]">
        <Field label="Target weight" hint={`Of ${tab.wholeName}: ${tab.wholeParts}.`}>
          <span className="flex items-center gap-1.5">
            <input
              type="text"
              inputMode="decimal"
              value={targetText}
              aria-invalid={isTargetUnusable}
              onChange={(event) => onTargetTextChange(event.target.value)}
              className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-24 aria-[invalid=true]:border-negative`}
            />
            <span className="font-medium text-muted">%</span>
          </span>
        </Field>
      </div>
      <PlanResult holdingInAccount={holdingInAccount} target={target} tab={tab} />
    </>
  );
}

/**
 * The drawer's Weight tab: how many shares to sell or buy for the holding to weigh a target percent of the whole account
 * (the stocks plus the cash, as in the Overview's graphs) — or, on an investor's tab, of all of that investor's money.
 * `tabs` are Portfolio and then every investor, and it opens on `initialInvestorId`'s (`null`: Portfolio); the target
 * typed in stays when the tab changes, to compare. A calculation only: nothing reaches IB and nothing is saved.
 * `syncedAt` is the last sync's time, which the price is from.
 */
export function WeightCalculatorPanel({
  holding,
  tabs,
  initialInvestorId,
  syncedAt,
}: {
  holding: Holding;
  tabs: InvestmentsAndCash[];
  initialInvestorId: number | null;
  syncedAt: string;
}) {
  const [selectedInvestorId, setSelectedInvestorId] = useState(initialInvestorId);
  const [targetText, setTargetText] = useState('');
  const tab = weightsTabOf(tabs, selectedInvestorId);
  const holdingInAccount = holdingInAccountOf(holding, tab);

  return (
    <div>
      {tabs.length > 1 && (
        <div className="mb-4">
          <WeightsTabs tabs={tabs} selectedInvestorId={tab.investorId} onSelect={setSelectedInvestorId} />
        </div>
      )}
      {'reason' in holdingInAccount ? (
        <InfoBand>{holdingInAccount.reason}</InfoBand>
      ) : (
        <>
          <WeightCalculator
            holdingInAccount={holdingInAccount}
            tab={tab}
            targetText={targetText}
            onTargetTextChange={setTargetText}
          />
          <p className="mt-4 text-caption text-faint">
            At IB’s last price, {formatMoney(holdingInAccount.price)}, from the sync of {formatDateTime(syncedAt)}. A
            calculation only: nothing is sold or bought.
          </p>
        </>
      )}
    </div>
  );
}
