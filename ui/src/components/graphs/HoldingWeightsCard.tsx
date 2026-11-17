import { useId, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { formatMoney, formatPercent } from '../../lib/format';
import type { OpenOnTab } from '../HoldingDrawer';
import { Tip } from '../Tip';
import type { InvestmentsAndCash } from './investmentsAndCash';
import { heaviestWeightsOf, type CashWeight, type HoldingWeight, type Weight } from './portfolioWeights';
import { CASH_COLOR_CLASS, STOCKS_COLOR_CLASS, WeightsCard, hintOf, stocksShareText } from './WeightsCard';

/** Past this many, the lightest holdings share one row, so the Overview stays short. */
const MOST_LISTED_HOLDINGS = 10;

/** One row: a holding (it opens its drawer), the lightest holdings together (they open Positions), or the cash. */
interface AccountPartBar {
  key: string;
  name: string;
  /** A holding's symbol is written in bold; "3 more" and "Cash" are lighter. */
  nameIsASymbol: boolean;
  amount: number;
  /** Of the whole account; `null` while it is unknown. Below zero for cash borrowed against the stocks. */
  percentOfAccount: number | null;
  /**
   * Where a click on the row goes — a holding's drawer opens on its Weight tab, on the same investor's weights; `null` for
   * the cash, which has no page.
   */
  link: { to: string; state?: OpenOnTab } | null;
  /** What the row's Tip says. */
  hint: string;
  barColorClass: string;
}

function holdingBarOf(holdingWeight: HoldingWeight, tab: InvestmentsAndCash): AccountPartBar {
  const { holding } = holdingWeight;
  return {
    key: `holding:${holding.id}`,
    name: holding.symbol,
    nameIsASymbol: true,
    amount: holdingWeight.marketValue,
    percentOfAccount: holdingWeight.percentOfAccount,
    link: {
      to: `/positions/${holding.id}`,
      state: { openOnTab: 'weight', weightsInvestorId: tab.investorId },
    },
    hint: stocksShareText(holdingWeight, tab.stocksName),
    barColorClass: STOCKS_COLOR_CLASS,
  };
}

function lightestHoldingsBarOf(
  lightestHoldingWeights: HoldingWeight[],
  total: Weight,
  tab: InvestmentsAndCash,
): AccountPartBar {
  const symbols = lightestHoldingWeights.map((holdingWeight) => holdingWeight.holding.symbol);
  return {
    key: 'lightest-holdings',
    name: `${symbols.length} more`,
    nameIsASymbol: false,
    amount: total.marketValue,
    percentOfAccount: total.percentOfAccount,
    link: { to: '/positions' },
    hint: hintOf([symbols.join(', '), stocksShareText(total, tab.stocksName)]),
    barColorClass: 'bg-chart-rest',
  };
}

function cashBarOf(cashWeight: CashWeight, tab: InvestmentsAndCash): AccountPartBar {
  return {
    key: 'cash',
    name: 'Cash',
    nameIsASymbol: false,
    amount: cashWeight.amount,
    percentOfAccount: cashWeight.percentOfAccount,
    link: null,
    hint: tab.cashHint,
    barColorClass: CASH_COLOR_CLASS,
  };
}

/** The heaviest holdings, then the lightest ones together. */
function holdingBarsOf(holdingWeights: HoldingWeight[], tab: InvestmentsAndCash): AccountPartBar[] {
  const { heaviest, rest } = heaviestWeightsOf(holdingWeights, MOST_LISTED_HOLDINGS);
  const bars = heaviest.map((holdingWeight) => holdingBarOf(holdingWeight, tab));
  if (rest !== null) bars.push(lightestHoldingsBarOf(rest.weights, rest.total, tab));
  return bars;
}

/** A bar can't be shorter than nothing: cash below zero draws no bar, and only its figures show. */
function drawnAmountOf(bar: AccountPartBar): number {
  return Math.max(bar.amount, 0);
}

/** The row's grey track, and in it the bar: the largest amount fills the whole track, the others are to the same scale. */
function BarInTrack({ bar, largestAmount }: { bar: AccountPartBar; largestAmount: number }) {
  const barLengthPercent = largestAmount > 0 ? (drawnAmountOf(bar) / largestAmount) * 100 : 0;
  return (
    <span className="block h-2.5 overflow-hidden rounded-[2px] bg-subtle" aria-hidden="true">
      {barLengthPercent > 0 && (
        <span
          className={`block h-full min-w-[2px] rounded-[2px] ${bar.barColorClass}`}
          style={{ width: `${barLengthPercent}%` }}
        />
      )}
    </span>
  );
}

/**
 * A link when the row leads somewhere, plain otherwise; either way laid out as one row of the card, reachable with the
 * keyboard, and highlighted while it is pointed at or has the keyboard's focus.
 */
function RowFrame({
  bar,
  tipId,
  onHighlight,
  children,
}: {
  bar: AccountPartBar;
  tipId: string;
  onHighlight: (key: string | null) => void;
  children: ReactNode;
}) {
  const rowClass =
    '-mx-1.5 grid h-7 grid-cols-[52px_minmax(0,1fr)_48px_84px] items-center gap-x-2 rounded-sm px-1.5 text-[13px]';
  const highlightWhilePointedAtOrFocused = {
    onMouseEnter: () => onHighlight(bar.key),
    onMouseLeave: () => onHighlight(null),
    onFocus: () => onHighlight(bar.key),
    onBlur: () => onHighlight(null),
  };
  if (bar.link === null) {
    return (
      <div tabIndex={0} aria-describedby={tipId} className={rowClass} {...highlightWhilePointedAtOrFocused}>
        {children}
      </div>
    );
  }
  return (
    <Link
      to={bar.link.to}
      state={bar.link.state}
      aria-describedby={tipId}
      className={`${rowClass} transition-colors hover:bg-subtle focus-visible:bg-subtle`}
      {...highlightWhilePointedAtOrFocused}
    >
      {children}
    </Link>
  );
}

/** One row — name · bar · percent · value — and its Tip above it. Its parent is `relative`, so the Tip sits over the row. */
function AccountPartRow({
  bar,
  largestAmount,
  isHighlighted,
  onHighlight,
}: {
  bar: AccountPartBar;
  largestAmount: number;
  isHighlighted: boolean;
  onHighlight: (key: string | null) => void;
}) {
  const tipId = useId();
  return (
    <>
      <RowFrame bar={bar} tipId={tipId} onHighlight={onHighlight}>
        <span className={`truncate ${bar.nameIsASymbol ? 'font-semibold' : 'font-medium text-muted'}`}>{bar.name}</span>
        <span>{bar.amount >= 0 && <BarInTrack bar={bar} largestAmount={largestAmount} />}</span>
        <span className="text-right font-semibold">{formatPercent(bar.percentOfAccount)}</span>
        <span className="text-right text-muted">{formatMoney(bar.amount)}</span>
      </RowFrame>
      <Tip id={tipId} text={bar.hint} isOpen={isHighlighted} />
    </>
  );
}

/**
 * The whole account by part — the tab's: all of an investor's money on their tab —: a bar for each holding, heaviest
 * first and each opening its drawer on the Weight tab, then one for the cash under a hairline. Together they add up to
 * 100%. Pointing at a row, or reaching it with the keyboard, opens its Tip.
 */
export function HoldingWeightsCard({
  tab,
  holdingWeights,
  cashWeight,
}: {
  tab: InvestmentsAndCash;
  holdingWeights: HoldingWeight[];
  cashWeight: CashWeight | null;
}) {
  const [highlightedKey, setHighlightedKey] = useState<string | null>(null);
  const holdingBars = holdingBarsOf(holdingWeights, tab);
  const cashBar = cashWeight === null ? null : cashBarOf(cashWeight, tab);
  const allBars = cashBar === null ? holdingBars : [...holdingBars, cashBar];
  const largestAmount = Math.max(...allBars.map(drawnAmountOf));

  return (
    <WeightsCard title="By holding" caption={`Share of ${tab.wholeName}`}>
      <ul>
        {holdingBars.map((bar) => (
          <li key={bar.key} className="relative">
            <AccountPartRow
              bar={bar}
              largestAmount={largestAmount}
              isHighlighted={bar.key === highlightedKey}
              onHighlight={setHighlightedKey}
            />
          </li>
        ))}
      </ul>
      {cashBar !== null && (
        <div className="relative mt-2 border-t pt-2">
          <AccountPartRow
            bar={cashBar}
            largestAmount={largestAmount}
            isHighlighted={cashBar.key === highlightedKey}
            onHighlight={setHighlightedKey}
          />
        </div>
      )}
    </WeightsCard>
  );
}
