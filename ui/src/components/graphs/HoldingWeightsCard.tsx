import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { formatMoney, formatPercent } from '../../lib/format';
import type { OpenOnTab } from '../HoldingDrawer';
import { heaviestWeightsOf, type CashWeight, type HoldingWeight, type Weight } from './portfolioWeights';
import { CASH_COLOR_CLASS, STOCKS_COLOR_CLASS, WeightsCard, hintOf, stocksShareText } from './WeightsCard';

/** Past this many, the lightest holdings share one row, so the Overview stays short. */
const MOST_LISTED_HOLDINGS = 10;

/** One row: a holding (it opens its drawer), the lightest holdings together (they open Positions), or the cash. */
interface AccountPartBar {
  key: string;
  name: string;
  amount: number;
  /** Of the whole account; `null` while it is unknown. Below zero for cash borrowed against the stocks. */
  percentOfAccount: number | null;
  /** Where a click on the row goes — a holding's drawer opens on its Weight tab; `null` for the cash, which has no page. */
  link: { to: string; state?: OpenOnTab } | null;
  /** What hovering the row adds to it. */
  hint: string;
  barColorClass: string;
}

function holdingBarOf(holdingWeight: HoldingWeight): AccountPartBar {
  const { holding } = holdingWeight;
  return {
    key: `holding:${holding.id}`,
    name: holding.symbol,
    amount: holdingWeight.marketValue,
    percentOfAccount: holdingWeight.percentOfAccount,
    link: { to: `/positions/${holding.id}`, state: { openOnTab: 'weight' } },
    hint: stocksShareText(holdingWeight),
    barColorClass: STOCKS_COLOR_CLASS,
  };
}

function lightestHoldingsBarOf(lightestHoldingWeights: HoldingWeight[], total: Weight): AccountPartBar {
  const symbols = lightestHoldingWeights.map((holdingWeight) => holdingWeight.holding.symbol);
  return {
    key: 'lightest-holdings',
    name: `${symbols.length} more`,
    amount: total.marketValue,
    percentOfAccount: total.percentOfAccount,
    link: { to: '/positions' },
    hint: hintOf([symbols.join(', '), stocksShareText(total)]),
    barColorClass: 'bg-chart-rest',
  };
}

function cashBarOf(cashWeight: CashWeight): AccountPartBar {
  return {
    key: 'cash',
    name: 'Cash',
    amount: cashWeight.amount,
    percentOfAccount: cashWeight.percentOfAccount,
    link: null,
    hint: 'The cash in the account, as IB reported it',
    barColorClass: CASH_COLOR_CLASS,
  };
}

/** The heaviest holdings, then the lightest ones together. */
function holdingBarsOf(holdingWeights: HoldingWeight[]): AccountPartBar[] {
  const { heaviest, rest } = heaviestWeightsOf(holdingWeights, MOST_LISTED_HOLDINGS);
  const bars = heaviest.map(holdingBarOf);
  if (rest !== null) bars.push(lightestHoldingsBarOf(rest.weights, rest.total));
  return bars;
}

/** A bar can't be shorter than nothing: an unknown or negative part draws none, and only its figure shows. */
function drawnPercentOf(bar: AccountPartBar): number {
  return Math.max(bar.percentOfAccount ?? 0, 0);
}

/** A link when the row leads somewhere, plain otherwise; either way laid out as one row of the card. */
function RowFrame({ bar, children }: { bar: AccountPartBar; children: ReactNode }) {
  const rowClass = 'grid grid-cols-[72px_minmax(0,1fr)_52px_96px] items-center gap-x-3 px-5 py-1.5';
  if (bar.link === null) {
    return (
      <div title={bar.hint} className={rowClass}>
        {children}
      </div>
    );
  }
  return (
    <Link
      to={bar.link.to}
      state={bar.link.state}
      title={bar.hint}
      className={`${rowClass} transition-colors hover:bg-subtle`}
    >
      {children}
    </Link>
  );
}

/** The longest bar spans the row's whole track, and the others are drawn to the same scale. */
function AccountPartRow({ bar, longestDrawnPercent }: { bar: AccountPartBar; longestDrawnPercent: number }) {
  const barLengthPercent = longestDrawnPercent > 0 ? (drawnPercentOf(bar) / longestDrawnPercent) * 100 : 0;
  return (
    <RowFrame bar={bar}>
      <span className="truncate font-semibold">{bar.name}</span>
      <span className="h-2.5" aria-hidden="true">
        {barLengthPercent > 0 && (
          <span
            className={`block h-full min-w-[2px] rounded-r ${bar.barColorClass}`}
            style={{ width: `${barLengthPercent}%` }}
          />
        )}
      </span>
      <span className="text-right font-semibold">{formatPercent(bar.percentOfAccount)}</span>
      <span className="text-right text-caption text-muted">{formatMoney(bar.amount)}</span>
    </RowFrame>
  );
}

/**
 * The whole account by part: a bar for each holding, heaviest first and each opening its drawer on the Weight tab, then
 * one for the cash under a hairline. Together they add up to 100%.
 */
export function HoldingWeightsCard({
  holdingWeights,
  cashWeight,
}: {
  holdingWeights: HoldingWeight[];
  cashWeight: CashWeight | null;
}) {
  const holdingBars = holdingBarsOf(holdingWeights);
  const cashBar = cashWeight === null ? null : cashBarOf(cashWeight);
  const allBars = cashBar === null ? holdingBars : [...holdingBars, cashBar];
  const longestDrawnPercent = Math.max(...allBars.map(drawnPercentOf));

  return (
    <WeightsCard title="By holding" caption="Share of the whole account">
      <ul className="pb-3">
        {holdingBars.map((bar) => (
          <li key={bar.key}>
            <AccountPartRow bar={bar} longestDrawnPercent={longestDrawnPercent} />
          </li>
        ))}
        {cashBar !== null && (
          <li className="mt-1.5 border-t pt-1.5">
            <AccountPartRow bar={cashBar} longestDrawnPercent={longestDrawnPercent} />
          </li>
        )}
      </ul>
    </WeightsCard>
  );
}
