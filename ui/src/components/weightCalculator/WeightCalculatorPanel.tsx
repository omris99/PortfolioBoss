import { useState, type ReactNode } from 'react';
import { formatDateTime, formatMoney, formatPercent, formatQuantity, formatSignedMoney, profitLossColorClass } from '../../lib/format';
import { INPUT_CLASS } from '../../lib/formInput';
import { NUMBER_INPUT_CLASS } from '../../lib/styles';
import type { Holding } from '../../types/portfolio';
import { Field, FormError } from '../FormFields';
import { weightsTabOf, type InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { WeightsTabs } from '../graphs/WeightsTabs';
import { WarnMark } from '../WarnMark';
import {
  holdingInAccountOf,
  percentOfAccountOf,
  targetPercentOf,
  weightPlanOf,
  type HoldingInAccount,
  type WeightPlan,
} from './targetWeight';

/** "Now 21.5% of the whole account · 21,520.00 of 100,000.00" — of all of Shay's money, on Shay's tab. */
function CurrentWeight({ holdingInAccount, tab }: { holdingInAccount: HoldingInAccount; tab: InvestmentsAndCash }) {
  const { quantity, price, wholeAccount } = holdingInAccount;
  return (
    <p className="mb-4 flex items-baseline gap-2">
      <span className="text-label text-muted">Now</span>
      <span className="text-figure">{formatPercent(percentOfAccountOf(holdingInAccount))}</span>
      <span className="text-muted">
        of {tab.wholeName} · {formatMoney(quantity * price)} of {formatMoney(wholeAccount.value)}
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
      <div className="mb-2 flex items-baseline gap-3 border-b pb-2">
        <span className="text-section-title">
          {isSale ? 'Sell' : 'Buy'} {plan.shareCount === 1 ? '1 share' : `${formatQuantity(plan.shareCount)} shares`}
        </span>
        <span className="ml-auto text-figure">≈ {formatMoney(plan.amount)}</span>
      </div>
      <PlanRow label={isSale ? 'After the sale' : 'After the purchase'}>
        {formatQuantity(plan.quantityAfter)} shares · {formatPercent(plan.percentAfter)} of {tab.wholeName}
      </PlanRow>
      <PlanRow label="Commission">≈ {formatMoney(plan.commission)}</PlanRow>
      {plan.action === 'sell' && (
        <PlanRow label="Realized P&L">
          <span className={profitLossColorClass(plan.realizedPnl)}>≈ {formatSignedMoney(plan.realizedPnl)}</span>
          <span className="text-muted"> at {tab.averageCostName}, the commission off</span>
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
function PlanResult({
  holdingInAccount,
  targetText,
  tab,
}: {
  holdingInAccount: HoldingInAccount;
  targetText: string;
  tab: InvestmentsAndCash;
}) {
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
  return <TradePlan plan={plan} cash={holdingInAccount.wholeAccount.cash} tab={tab} />;
}

/** The calculator on one tab: the weight now, the target field, and what the target comes to. */
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
  return (
    <>
      <CurrentWeight holdingInAccount={holdingInAccount} tab={tab} />
      <Field label="Target weight" hint={`Of ${tab.wholeName}: ${tab.wholeParts}.`}>
        <span className="flex items-center gap-2">
          <input
            type="text"
            inputMode="decimal"
            value={targetText}
            onChange={(event) => onTargetTextChange(event.target.value)}
            className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-24`}
          />
          <span className="text-muted">%</span>
        </span>
      </Field>
      <PlanResult holdingInAccount={holdingInAccount} targetText={targetText} tab={tab} />
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
        <p className="py-6 text-muted">{holdingInAccount.reason}</p>
      ) : (
        <>
          <WeightCalculator
            holdingInAccount={holdingInAccount}
            tab={tab}
            targetText={targetText}
            onTargetTextChange={setTargetText}
          />
          <p className="mt-4 text-caption text-muted">
            At IB’s last price, {formatMoney(holdingInAccount.price)}, from the sync of {formatDateTime(syncedAt)}. A
            calculation only: nothing is sold or bought.
          </p>
        </>
      )}
    </div>
  );
}
