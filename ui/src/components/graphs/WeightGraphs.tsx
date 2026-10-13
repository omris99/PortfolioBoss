import type { Holding } from '../../types/portfolio';
import { HoldingWeightsCard } from './HoldingWeightsCard';
import { portfolioWeightsOf, type LeftOutHolding } from './portfolioWeights';
import { SectorWeightsCard } from './SectorWeightsCard';

/** "Not in the graphs: TCEHY (in HKD)." — nothing while every open holding is weighed. */
function LeftOutNote({ leftOut }: { leftOut: LeftOutHolding[] }) {
  if (leftOut.length === 0) return null;
  const leftOutText = leftOut.map(({ holding, reason }) => `${holding.symbol} (${reason})`).join(', ');
  return <p className="text-caption text-muted">Not in the graphs: {leftOutText}.</p>;
}

/**
 * The Overview's two weight graphs side by side — by sector, and by holding with the cash — and the holdings they leave
 * out. `cash` is IB's cash in the account. Nothing while there is no open holding to weigh.
 */
export function WeightGraphs({ openHoldings, cash }: { openHoldings: Holding[]; cash: number | null }) {
  const portfolioWeights = portfolioWeightsOf(openHoldings, cash);
  if (portfolioWeights.byHolding.length === 0) return null;

  return (
    <div className="flex flex-col gap-2">
      <div className="grid grid-cols-2 items-stretch gap-6">
        <SectorWeightsCard sectorWeights={portfolioWeights.bySector} noSectorWeight={portfolioWeights.noSector} />
        <HoldingWeightsCard holdingWeights={portfolioWeights.byHolding} cashWeight={portfolioWeights.cash} />
      </div>
      <LeftOutNote leftOut={portfolioWeights.leftOut} />
    </div>
  );
}
