import { useState } from 'react';
import { formatDate, formatMoney, formatPercent } from '../../lib/format';
import { INPUT_CLASS } from '../../lib/formInput';
import { NUMBER_INPUT_CLASS } from '../../lib/styles';
import type { Holding } from '../../types/portfolio';
import type { HotStock } from '../../types/recommendations';
import { Field, FormError, InfoBand, SelectInput } from '../FormFields';
import { weightsTabOf, type InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { WeightsTabs } from '../graphs/WeightsTabs';
import { sectorText } from '../recommendations/hotStockTexts';
import { amountOf, matchingSectorOf, purchaseOf, sectorsOfHoldings, type Purchase, type SectorChoice } from './ifIBuy';
import { CashCoverage, PlanRow, shareCountText } from './WeightCalculatorPanel';

/** The select's value for "A sector not held yet"; every other value is one of the user's sectors, or '' for none. */
const NOT_HELD_YET = '\u0000not-held-yet';

function sectorChoiceOf(selectValue: string): SectorChoice | null {
  if (selectValue === '') return null;
  if (selectValue === NOT_HELD_YET) return { kind: 'notHeldYet' };
  return { kind: 'sector', name: selectValue };
}

/** The sector's name in the result: the user's, or IB's first word for one not held yet. */
function sectorNameOf(sectorChoice: SectorChoice, stock: HotStock): string {
  if (sectorChoice.kind === 'sector') return sectorChoice.name;
  return stock.industry ?? 'Its sector';
}

/** "Buy 59 shares ≈ 4,967.80", then the commission, the stock and its sector after, and the cash. */
function PurchaseResult({
  purchase,
  stock,
  sectorChoice,
  tab,
}: {
  purchase: Purchase;
  stock: HotStock;
  sectorChoice: SectorChoice | null;
  tab: InvestmentsAndCash;
}) {
  return (
    <div className="mt-4 rounded-lg border px-4 py-3">
      <div className="flex items-baseline justify-between gap-4 pb-2">
        <span className="text-[16px] font-semibold leading-6">Buy {shareCountText(purchase.shareCount)}</span>
        <span className="text-[16px] font-medium">≈ {formatMoney(purchase.cost)}</span>
      </div>
      <dl>
        <PlanRow label="Commission">≈ {formatMoney(purchase.commission)}</PlanRow>
        <PlanRow label={`${stock.symbol} after`}>
          {formatPercent(purchase.stockPercentAfter)} of {tab.wholeName}
          {purchase.heldQuantity > 0 && (
            <span className="block text-caption text-faint">
              with the {shareCountText(purchase.heldQuantity)} already held
            </span>
          )}
        </PlanRow>
        <PlanRow label={sectorChoice === null ? 'Its sector after' : `${sectorNameOf(sectorChoice, stock)} after`}>
          {purchase.sectorAfter === null ? (
            <span className="text-muted">Pick a sector to see it</span>
          ) : (
            <>
              {formatPercent(purchase.sectorAfter.percentBefore)} → {formatPercent(purchase.sectorAfter.percentAfter)}
              <span className="block text-caption text-faint">of {tab.stocksName}</span>
            </>
          )}
        </PlanRow>
        <PlanRow label="Cash">
          <CashCoverage cash={purchase.cash} isCoveredByCash={purchase.isCoveredByCash} />
        </PlanRow>
      </dl>
    </div>
  );
}

/** What the amount typed in comes to on the tab — or what to type, or why it can't be worked out. */
function IfIBuyResult({
  amountText,
  stock,
  heldAs,
  sectorChoice,
  tab,
}: {
  amountText: string;
  stock: HotStock;
  heldAs: Holding | null;
  sectorChoice: SectorChoice | null;
  tab: InvestmentsAndCash;
}) {
  const amount = amountOf(amountText);
  if (amount === null) {
    return (
      <p className="mt-4 rounded-lg border border-dashed border-strong px-4 py-5 text-center text-muted">
        Type an amount to see the shares it buys, and the stock’s and its sector’s weights after.
      </p>
    );
  }
  if (typeof amount !== 'number') return <FormError>{amount.reason}</FormError>;
  const purchase = purchaseOf(tab, stock.momentum.lastClose, amount, heldAs, sectorChoice);
  if ('reason' in purchase) return <InfoBand>{purchase.reason}</InfoBand>;
  return <PurchaseResult purchase={purchase} stock={stock} sectorChoice={sectorChoice} tab={tab} />;
}

/**
 * The stock drawer's If I buy tab (DESIGN_BRIEF.md §8.3): an amount in dollars → whole shares at the last close, the
 * commission, and the stock's and its sector's weights after, on each tab (Portfolio, then every investor), the cash
 * checked like the Weight tab's purchase. The sector is picked from the user's own — already picked when one of IB's
 * words for it matches. The amount and the sector stay when the tab changes, to compare. A calculation only: nothing
 * is bought, nothing is saved.
 */
export function IfIBuyPanel({
  stock,
  tabs,
  openHoldings,
}: {
  stock: HotStock;
  tabs: InvestmentsAndCash[];
  openHoldings: Holding[];
}) {
  const sectors = sectorsOfHoldings(openHoldings);
  const matchingSector = matchingSectorOf([stock.industry, stock.category], sectors);
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | null>(null);
  const [amountText, setAmountText] = useState('');
  const [sectorValue, setSectorValue] = useState(matchingSector ?? '');
  const tab = weightsTabOf(tabs, selectedInvestorId);
  const heldAs = openHoldings.find((holding) => holding.conId === stock.conId) ?? null;
  const amount = amountOf(amountText);
  const isAmountUnusable = amount !== null && typeof amount !== 'number';
  const sectorHint =
    matchingSector !== null && sectorValue === matchingSector
      ? `IB: ${sectorText(stock)} · picked for you`
      : `IB: ${sectorText(stock)}`;

  return (
    <div>
      {tabs.length > 1 && (
        <div className="mb-4">
          <WeightsTabs tabs={tabs} selectedInvestorId={tab.investorId} onSelect={setSelectedInvestorId} />
        </div>
      )}
      <div className="grid max-w-[480px] grid-cols-[140px_1fr] gap-4">
        <Field label="Amount (USD)">
          <input
            type="text"
            inputMode="decimal"
            value={amountText}
            aria-invalid={isAmountUnusable}
            onChange={(event) => setAmountText(event.target.value)}
            className={`${INPUT_CLASS} ${NUMBER_INPUT_CLASS} w-full aria-[invalid=true]:border-negative`}
          />
        </Field>
        <Field label="Sector, from your list" hint={sectorHint}>
          <SelectInput value={sectorValue} onChange={(event) => setSectorValue(event.target.value)}>
            <option value="">Pick a sector</option>
            {sectors.map((sector) => (
              <option key={sector} value={sector}>
                {sector}
              </option>
            ))}
            <option value={NOT_HELD_YET}>A sector not held yet</option>
          </SelectInput>
        </Field>
      </div>
      <IfIBuyResult
        amountText={amountText}
        stock={stock}
        heldAs={heldAs}
        sectorChoice={sectorChoiceOf(sectorValue)}
        tab={tab}
      />
      <p className="mt-4 text-caption text-faint">
        At the last close, {formatMoney(stock.momentum.lastClose)}, of {formatDate(stock.momentum.asOf)}. A calculation
        only: nothing is sold or bought.
      </p>
    </div>
  );
}
