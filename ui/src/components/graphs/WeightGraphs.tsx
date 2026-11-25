import { useState } from 'react';
import { Info } from 'lucide-react';
import { formatMoney } from '../../lib/format';
import { CARD_CLASS } from '../../lib/styles';
import { SkeletonBar } from '../firstLoad/SkeletonBar';
import { HoldingWeightsCard, HoldingWeightsCardSkeleton } from './HoldingWeightsCard';
import { weightsTabOf, type InvestmentsAndCash } from './investmentsAndCash';
import { portfolioWeightsOf, type LeftOutHolding, type PortfolioWeights } from './portfolioWeights';
import { InvestorTabs } from '../InvestorTabs';
import { SectorWeightsCard, SectorWeightsCardSkeleton } from './SectorWeightsCard';

/** "Not in the graphs: TCEHY (in HKD)." — nothing while every open holding is weighed. */
function LeftOutNote({ leftOut }: { leftOut: LeftOutHolding[] }) {
  if (leftOut.length === 0) return null;
  const leftOutText = leftOut.map(({ holding, reason }) => `${holding.symbol} (${reason})`).join(', ');
  return (
    <p className="flex items-center gap-1.5 px-5 pb-4 text-caption text-muted">
      <Info size={14} className="shrink-0" />
      Not in the graphs: {leftOutText}.
    </p>
  );
}

/**
 * On an investor's tab with nothing to weigh, in place of the two graphs: "Shay holds no stocks the graphs can weigh ·
 * cash 2,000.00".
 */
function NothingToWeigh({ tab }: { tab: InvestmentsAndCash }) {
  return (
    <p className="px-5 py-6 text-center text-muted">
      {tab.name} holds no stocks the graphs can weigh · cash {formatMoney(tab.cash)}
    </p>
  );
}

const CARD_TITLE = 'Weights';
/** The card's title row, with the tabs beside the title. */
const HEADING_ROW_CLASS = 'flex items-center gap-4 border-b px-5 pb-3 pt-4';
/** The two halves side by side, each as tall as the other, with a line between them. */
const HALVES_CLASS = 'grid grid-cols-2 items-stretch divide-x';

/** The two graphs as the card's two halves. */
function SectorAndHoldingHalves({ tab, portfolioWeights }: { tab: InvestmentsAndCash; portfolioWeights: PortfolioWeights }) {
  return (
    <div className={HALVES_CLASS}>
      <SectorWeightsCard tab={tab} sectorWeights={portfolioWeights.bySector} noSectorWeight={portfolioWeights.noSector} />
      <HoldingWeightsCard tab={tab} holdingWeights={portfolioWeights.byHolding} cashWeight={portfolioWeights.cash} />
    </div>
  );
}

/**
 * The Overview's weights, all in one card: its title with a tab for each investor's own weights (`tabs`: Portfolio,
 * then every investor) over a line, then the two graphs side by side — by sector, and by holding with the cash — and the
 * holdings they leave out. One card around everything shows that a tab is one choice for both graphs. Nothing while the
 * whole account has no open holding to weigh.
 */
export function WeightGraphs({ tabs }: { tabs: InvestmentsAndCash[] }) {
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | null>(null);
  const portfolioTab = tabs[0];
  if (portfolioWeightsOf(portfolioTab).byHolding.length === 0) return null;

  const selectedTab = weightsTabOf(tabs, selectedInvestorId);
  const portfolioWeights = portfolioWeightsOf(selectedTab);

  return (
    <section aria-label={CARD_TITLE} className={CARD_CLASS}>
      <div className={HEADING_ROW_CLASS}>
        <h2 className="text-section-title">{CARD_TITLE}</h2>
        <InvestorTabs
          tabs={tabs}
          selectedInvestorId={selectedTab.investorId}
          onSelect={setSelectedInvestorId}
          ariaLabel="Whose weights"
        />
      </div>
      {portfolioWeights.byHolding.length === 0 ? (
        <NothingToWeigh tab={selectedTab} />
      ) : (
        <SectorAndHoldingHalves tab={selectedTab} portfolioWeights={portfolioWeights} />
      )}
      <LeftOutNote leftOut={portfolioWeights.leftOut} />
    </section>
  );
}

/**
 * The same card during the first load: its title, a grey bar for the tabs (the investors' names come with the data),
 * and the two halves with their titles — the ring in grey and eight grey bars.
 */
export function WeightGraphsSkeleton() {
  return (
    <section aria-label={CARD_TITLE} aria-busy="true" className={CARD_CLASS}>
      <div className={HEADING_ROW_CLASS}>
        <h2 className="text-section-title">{CARD_TITLE}</h2>
        <SkeletonBar width={220} height={32} />
      </div>
      <div className={HALVES_CLASS}>
        <SectorWeightsCardSkeleton />
        <HoldingWeightsCardSkeleton />
      </div>
    </section>
  );
}
