import { openHoldingsOf } from '../lib/holdings';
import type { PortfolioSnapshot } from '../types/portfolio';
import { AccountSummary } from '../components/AccountSummary';
import { WeightGraphs } from '../components/graphs/WeightGraphs';
import { InvestorsStrip } from '../components/InvestorsStrip';
import { NeedsAttention } from '../components/NeedsAttention';
import { PageHeader } from '../components/PageHeader';
import { SignalGlance } from '../components/SignalGlance';

/**
 * The Overview (/): the account's four figures with the stocks' and the cash's parts, what needs a look beside how the
 * signals stand, the weight of each sector and each holding, and each investor's total when there are several. Only
 * figures the API already sends, and what is worked out from them.
 */
export function OverviewPage({ snapshot }: { snapshot: PortfolioSnapshot }) {
  const openHoldings = openHoldingsOf(snapshot.holdings);

  return (
    <>
      <PageHeader title="Overview" />
      <div className="flex flex-col gap-6">
        <AccountSummary snapshot={snapshot} openHoldings={openHoldings} />
        <div className="grid grid-cols-[minmax(0,2fr)_minmax(300px,1fr)] items-stretch gap-6">
          <NeedsAttention holdings={snapshot.holdings} />
          <SignalGlance openHoldings={openHoldings} />
        </div>
        <WeightGraphs openHoldings={openHoldings} cash={snapshot.totalCashValue} />
        <InvestorsStrip investors={snapshot.investors} />
      </div>
    </>
  );
}
