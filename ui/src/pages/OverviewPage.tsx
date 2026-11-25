import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { openHoldingsOf } from '../lib/holdings';
import type { PortfolioSnapshot } from '../types/portfolio';
import { AccountSummary, AccountSummarySkeleton } from '../components/AccountSummary';
import { weightsTabsOf } from '../components/graphs/investmentsAndCash';
import { WeightGraphs, WeightGraphsSkeleton } from '../components/graphs/WeightGraphs';
import { InvestorsStrip, InvestorsStripSkeleton } from '../components/InvestorsStrip';
import { NeedsAttention, NeedsAttentionSkeleton } from '../components/NeedsAttention';
import { PageHeader } from '../components/PageHeader';
import { SignalGlance, SignalGlanceSkeleton } from '../components/SignalGlance';

/** Needs attention beside the signal's colours, the first about twice as wide. */
const ATTENTION_AND_SIGNAL_ROW_CLASS = 'grid grid-cols-[minmax(0,2fr)_minmax(300px,1fr)] items-stretch gap-6';

/** The cards of the page, the same while it loads and once it has the data. */
const CARDS_CLASS = 'flex flex-col gap-6';

/** Every card in its own frame, with grey bars where its figures will be (round 6's first load). */
function OverviewCardsWhileLoading() {
  return (
    <div className={CARDS_CLASS}>
      <AccountSummarySkeleton />
      <div className={ATTENTION_AND_SIGNAL_ROW_CLASS}>
        <NeedsAttentionSkeleton />
        <SignalGlanceSkeleton />
      </div>
      <WeightGraphsSkeleton />
      <InvestorsStripSkeleton />
    </div>
  );
}

function OverviewCards({ snapshot }: { snapshot: PortfolioSnapshot }) {
  const openHoldings = openHoldingsOf(snapshot.holdings);
  return (
    <div className={CARDS_CLASS}>
      <AccountSummary snapshot={snapshot} openHoldings={openHoldings} />
      <div className={ATTENTION_AND_SIGNAL_ROW_CLASS}>
        <NeedsAttention holdings={snapshot.holdings} />
        <SignalGlance openHoldings={openHoldings} />
      </div>
      <WeightGraphs tabs={weightsTabsOf(openHoldings, snapshot.totalCashValue, snapshot.investors)} />
      <InvestorsStrip investors={snapshot.investors} />
    </div>
  );
}

/**
 * The Overview (/): the account's four figures with the stocks' and the cash's parts, what needs a look beside how the
 * signals stand, the weight of each sector and each holding — of the whole account and of each investor's own money —,
 * and each investor's total when there are several. Only figures the API already sends, and what is worked out from
 * them. `snapshot` is `null` during the first load: the cards then show their frames with grey bars.
 */
export function OverviewPage({ snapshot }: { snapshot: PortfolioSnapshot | null }) {
  useDocumentTitle('Overview');

  return (
    <>
      <PageHeader
        title="Overview"
        description="The account at the last sync: what needs a look, the signal, the weights."
      />
      {snapshot === null ? <OverviewCardsWhileLoading /> : <OverviewCards snapshot={snapshot} />}
    </>
  );
}
