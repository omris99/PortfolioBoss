import { useState } from 'react';
import { formatMoney } from '../../lib/format';
import { CARD_CLASS } from '../../lib/styles';
import { HoldingWeightsCard } from './HoldingWeightsCard';
import { weightsTabOf, type InvestmentsAndCash } from './investmentsAndCash';
import { portfolioWeightsOf, type LeftOutHolding, type PortfolioWeights } from './portfolioWeights';
import { SectorWeightsCard } from './SectorWeightsCard';
import { WeightsTabs } from './WeightsTabs';

/** "Not in the graphs: TCEHY (in HKD)." — nothing while every open holding is weighed. */
function LeftOutNote({ leftOut }: { leftOut: LeftOutHolding[] }) {
  if (leftOut.length === 0) return null;
  const leftOutText = leftOut.map(({ holding, reason }) => `${holding.symbol} (${reason})`).join(', ');
  return <p className="text-caption text-muted">Not in the graphs: {leftOutText}.</p>;
}

/** On an investor's tab with nothing to weigh: "Shay holds no stocks the graphs can weigh · cash 2,000.00". */
function NothingToWeigh({ tab }: { tab: InvestmentsAndCash }) {
  return (
    <p className={`${CARD_CLASS} px-5 py-6 text-center text-muted`}>
      {tab.name} holds no stocks the graphs can weigh · cash {formatMoney(tab.cash)}
    </p>
  );
}

function SectorAndHoldingCards({ tab, portfolioWeights }: { tab: InvestmentsAndCash; portfolioWeights: PortfolioWeights }) {
  return (
    <div className="grid grid-cols-2 items-stretch gap-6">
      <SectorWeightsCard tab={tab} sectorWeights={portfolioWeights.bySector} noSectorWeight={portfolioWeights.noSector} />
      <HoldingWeightsCard tab={tab} holdingWeights={portfolioWeights.byHolding} cashWeight={portfolioWeights.cash} />
    </div>
  );
}

/**
 * The Overview's two weight graphs side by side — by sector, and by holding with the cash — and the holdings they leave
 * out, with a tab for each investor's own weights above them (`tabs`: Portfolio, then every investor). Nothing while
 * the whole account has no open holding to weigh.
 */
export function WeightGraphs({ tabs }: { tabs: InvestmentsAndCash[] }) {
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | null>(null);
  const portfolioTab = tabs[0];
  if (portfolioWeightsOf(portfolioTab).byHolding.length === 0) return null;

  const selectedTab = weightsTabOf(tabs, selectedInvestorId);
  const portfolioWeights = portfolioWeightsOf(selectedTab);

  return (
    <div className="flex flex-col gap-2">
      <WeightsTabs tabs={tabs} selectedInvestorId={selectedTab.investorId} onSelect={setSelectedInvestorId} />
      {portfolioWeights.byHolding.length === 0 ? (
        <NothingToWeigh tab={selectedTab} />
      ) : (
        <SectorAndHoldingCards tab={selectedTab} portfolioWeights={portfolioWeights} />
      )}
      <LeftOutNote leftOut={portfolioWeights.leftOut} />
    </div>
  );
}
