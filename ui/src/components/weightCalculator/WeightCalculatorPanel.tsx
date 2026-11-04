import { useState, type ReactNode } from 'react';
import { Check } from 'lucide-react';
import {
  ACCOUNT_CURRENCY,
  formatDateTime,
  formatMoney,
  formatPercent,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercentOneDecimal,
  profitLossColorClass,
} from '../../lib/format';
import { INPUT_CLASS } from '../../lib/formInput';
import { NUMBER_INPUT_CLASS } from '../../lib/styles';
import type { Holding } from '../../types/portfolio';
import { Field, FormError, InfoBand } from '../FormFields';
import { weightsTabOf, type InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { WeightsTabs } from '../graphs/WeightsTabs';
import { WarnMark } from '../WarnMark';
import { amountOf } from './ifIBuy';
import {
  buildPurchasePlanForAmount,
  holdingInAccountOf,
  percentOfAccountOf,
  targetPercentOf,
  weightPlanOf,
  type AverageCostAfterPurchase,
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

/**
 * "New average 46.70 (now 50.00)" and "To reach the average +16.8% (now +25.0%)": how far the price must rise from
 * today's to reach the average, after the purchase and now. With the price already at or above the average, the second
 * row says how far above it is: "Already reached: the price is 4.0% above the average".
 */
function AverageCostAfterPurchaseRows({
  averageCostAfterPurchase,
  tab,
}: {
  averageCostAfterPurchase: AverageCostAfterPurchase;
  tab: InvestmentsAndCash;
}) {
  const {
    currentAverageCost,
    newAverageCost,
    riseNeededToReachCurrentAveragePercent,
    riseNeededToReachNewAveragePercent,
    priceAboveCurrentAveragePercent,
  } = averageCostAfterPurchase;
  const isPriceBelowAverage = riseNeededToReachCurrentAveragePercent > 0;
  return (
    <>
      <PlanRow label="New average">
        {formatMoney(newAverageCost)} <span className="text-muted">(now {formatMoney(currentAverageCost)})</span>
        <span className="block text-caption text-faint">the commission included, as in {tab.averageCostName}</span>
      </PlanRow>
      <PlanRow label="To reach the average">
        {isPriceBelowAverage ? (
          <>
            {formatSignedPercentOneDecimal(riseNeededToReachNewAveragePercent)}{' '}
            <span className="text-muted">
              (now {formatSignedPercentOneDecimal(riseNeededToReachCurrentAveragePercent)})
            </span>
          </>
        ) : (
          <>Already reached: the price is {formatPercent(priceAboveCurrentAveragePercent)} above the average</>
        )}
      </PlanRow>
    </>
  );
}

/** "Sell 31 shares ≈ 6,671.20", then what the account looks like after it — and, after a purchase, the new average. */
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
        {plan.action === 'buy' && plan.averageCostAfterPurchase !== null && (
          <AverageCostAfterPurchaseRows averageCostAfterPurchase={plan.averageCostAfterPurchase} tab={tab} />
        )}
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

/** What the target weight typed in comes to, or why it can't be used. */
function TargetWeightResult({
  holdingInAccount,
  target,
  tab,
}: {
  holdingInAccount: HoldingInAccount;
  target: number | CannotCalculate;
  tab: InvestmentsAndCash;
}) {
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

/** What the amount typed in buys, or why it can't be used. */
function PurchaseAmountResult({
  holdingInAccount,
  amount,
  tab,
}: {
  holdingInAccount: HoldingInAccount;
  amount: number | CannotCalculate;
  tab: InvestmentsAndCash;
}) {
  if (typeof amount !== 'number') return <FormError>{amount.reason}</FormError>;

  const plan = buildPurchasePlanForAmount(holdingInAccount, amount);
  if ('reason' in plan) return <FormError>{plan.reason}</FormError>;
  return <TradePlan plan={plan} cash={holdingInAccount.wholeAccount.cash} tab={tab} />;
}

/** What the target weight or the amount typed in comes to — only one of them is typed at a time —, or what to type. */
function PlanResult({
  holdingInAccount,
  target,
  amount,
  tab,
}: {
  holdingInAccount: HoldingInAccount;
  /** The percent typed in; `null` while nothing is typed. */
  target: number | CannotCalculate | null;
  /** The amount typed in, in dollars; `null` while nothing is typed. */
  amount: number | CannotCalculate | null;
  tab: InvestmentsAndCash;
}) {
  if (target !== null) return <TargetWeightResult holdingInAccount={holdingInAccount} target={target} tab={tab} />;
  if (amount !== null) return <PurchaseAmountResult holdingInAccount={holdingInAccount} amount={amount} tab={tab} />;
  return (
    <p className="mt-4 rounded-lg border border-dashed border-strong px-4 py-5 text-center text-muted">
      Type a target weight to see how many shares to sell or buy, or an amount to see what it buys.
    </p>
  );
}

/** A field for a number with its unit after it ("%", "USD"), its border red while what is typed can't be used. */
function NumberInputWithUnit({
  typedText,
  unit,
  isUnusable,
  onTypedTextChange,
}: {
  typedText: string;
  unit: string;
  isUnusable: boolean;
  onTypedTextChange: (typedText: string) => void;
}) {
  return (
    <span className="flex items-center gap-1.5">
      <input
        type="text"
        inputMode="decimal"
        value={typedText}
        aria-invalid={isUnusable}
        onChange={(event) => onTypedTextChange(event.target.value)}
        className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-24 aria-[invalid=true]:border-negative`}
      />
      <span className="font-medium text-muted">{unit}</span>
    </span>
  );
}

/**
 * The calculator on one tab: the weight now, the two fields — a target weight, or an amount to buy for — and what the
 * one typed in comes to.
 */
function WeightCalculator({
  holdingInAccount,
  tab,
  targetText,
  amountText,
  onTargetTextChange,
  onAmountTextChange,
}: {
  holdingInAccount: HoldingInAccount;
  tab: InvestmentsAndCash;
  targetText: string;
  amountText: string;
  onTargetTextChange: (typedText: string) => void;
  onAmountTextChange: (typedText: string) => void;
}) {
  const target = targetPercentOf(targetText);
  const amount = amountOf(amountText);
  const isTargetUnusable = target !== null && typeof target !== 'number';
  const isAmountUnusable = amount !== null && typeof amount !== 'number';
  return (
    <>
      <CurrentWeight holdingInAccount={holdingInAccount} tab={tab} />
      <div className="grid max-w-[480px] grid-cols-2 gap-4">
        <Field label="Target weight" hint={`Of ${tab.wholeName}: ${tab.wholeParts}.`}>
          <NumberInputWithUnit
            typedText={targetText}
            unit="%"
            isUnusable={isTargetUnusable}
            onTypedTextChange={onTargetTextChange}
          />
        </Field>
        <Field label="Or buy for an amount" hint="In whole shares, at IB’s last price.">
          <NumberInputWithUnit
            typedText={amountText}
            unit={ACCOUNT_CURRENCY}
            isUnusable={isAmountUnusable}
            onTypedTextChange={onAmountTextChange}
          />
        </Field>
      </div>
      <PlanResult holdingInAccount={holdingInAccount} target={target} amount={amount} tab={tab} />
    </>
  );
}

/**
 * The drawer's Weight tab: how many shares to sell or buy for the holding to weigh a target percent of the whole account
 * (the stocks plus the cash, as in the Overview's graphs) — or, on an investor's tab, of all of that investor's money —,
 * or how many an amount buys; and for a purchase, the new average cost and how far the price must rise to reach it.
 * `tabs` are Portfolio and then every investor, and it opens on `initialInvestorId`'s (`null`: Portfolio); what is typed
 * stays when the tab changes, to compare, and typing in one field empties the other (MY_HOLDINGS_TODO.md, decision 8). A
 * calculation only: nothing reaches IB and nothing is saved. `syncedAt` is the last sync's time, which the price is from.
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
  const [amountText, setAmountText] = useState('');
  const tab = weightsTabOf(tabs, selectedInvestorId);
  const holdingInAccount = holdingInAccountOf(holding, tab);

  function changeTargetText(typedText: string) {
    setTargetText(typedText);
    setAmountText('');
  }

  function changeAmountText(typedText: string) {
    setAmountText(typedText);
    setTargetText('');
  }

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
            amountText={amountText}
            onTargetTextChange={changeTargetText}
            onAmountTextChange={changeAmountText}
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
