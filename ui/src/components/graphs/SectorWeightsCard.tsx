import { useId, useState } from 'react';
import { formatMoney, formatPercent } from '../../lib/format';
import { Tip } from '../Tip';
import { DonutChart, type DonutSlice } from './DonutChart';
import type { InvestmentsAndCash } from './investmentsAndCash';
import { heaviestWeightsOf, type NoSectorWeight, type SectorWeight, type Weight } from './portfolioWeights';
import { WeightsCard, accountShareText, countText, hintOf } from './WeightsCard';

/** How a slice of the ring and its swatch in the legend are painted. */
interface SlicePaint {
  fillClass: string;
  swatchClass: string;
}

/** The colored sectors, heaviest first, in the ring's order (index.css explains the order). */
const SECTOR_PAINTS: SlicePaint[] = [
  { fillClass: 'fill-chart-1', swatchClass: 'bg-chart-1' },
  { fillClass: 'fill-chart-2', swatchClass: 'bg-chart-2' },
  { fillClass: 'fill-chart-3', swatchClass: 'bg-chart-3' },
  { fillClass: 'fill-chart-4', swatchClass: 'bg-chart-4' },
];

/** The lighter sectors, added together once every color is taken. */
const OTHER_SECTORS_PAINT: SlicePaint = { fillClass: 'fill-chart-rest', swatchClass: 'bg-chart-rest' };

/** Only an outline, like a holding with no signal: a sector not entered yet is unknown, not a sector of its own. */
const NO_SECTOR_PAINT: SlicePaint = {
  fillClass: 'fill-surface stroke-signal-none stroke-[1.5]',
  swatchClass: 'ring-[1.5px] ring-inset ring-signal-none',
};

/** The size of the figure in the ring's hole: the number of sectors, or the percent of the slice pointed at. */
const RING_CENTER_FIGURE_CLASS = 'text-[20px] font-semibold leading-6';

/** One slice of the ring, with its row in the legend. */
interface SectorSlice {
  key: string;
  name: string;
  weight: Weight;
  /** What the row's Tip says. */
  hint: string;
  paint: SlicePaint;
}

function coloredSectorSlicesOf(sectorWeights: SectorWeight[], wholeName: string): SectorSlice[] {
  return sectorWeights.map((sectorWeight, index) => ({
    key: `sector:${sectorWeight.sector}`,
    name: sectorWeight.sector,
    weight: sectorWeight,
    hint: hintOf([countText(sectorWeight.holdingCount, 'holding'), accountShareText(sectorWeight, wholeName)]),
    paint: SECTOR_PAINTS[index],
  }));
}

/** Its hint names the sectors and counts their holdings: "Financials: 1 holding · 9.1% of the whole account". */
function otherSectorsSliceOf(otherSectorWeights: SectorWeight[], total: Weight, wholeName: string): SectorSlice {
  const sectorNames = otherSectorWeights.map((sectorWeight) => sectorWeight.sector).join(', ');
  const holdingCountOfAllOtherSectors = otherSectorWeights.reduce(
    (runningCount, sectorWeight) => runningCount + sectorWeight.holdingCount,
    0,
  );
  return {
    key: 'other-sectors',
    name: `Other (${countText(otherSectorWeights.length, 'sector')})`,
    weight: total,
    hint: hintOf([
      `${sectorNames}: ${countText(holdingCountOfAllOtherSectors, 'holding')}`,
      accountShareText(total, wholeName),
    ]),
    paint: OTHER_SECTORS_PAINT,
  };
}

function noSectorSliceOf(noSectorWeight: NoSectorWeight, wholeName: string): SectorSlice {
  return {
    key: 'no-sector',
    name: 'No sector',
    weight: noSectorWeight,
    hint: hintOf([
      `${countText(noSectorWeight.holdingCount, 'holding')} without a sector yet: add it in Positions`,
      accountShareText(noSectorWeight, wholeName),
    ]),
    paint: NO_SECTOR_PAINT,
  };
}

/**
 * The heaviest sectors in their colors, then the others together, then the holdings with no sector. `wholeName` names
 * the tab's whole account in the hints: "the whole account", "all of Shay's money".
 */
function sectorSlicesOf(
  sectorWeights: SectorWeight[],
  noSectorWeight: NoSectorWeight | null,
  wholeName: string,
): SectorSlice[] {
  const { heaviest, rest } = heaviestWeightsOf(sectorWeights, SECTOR_PAINTS.length);
  const slices = coloredSectorSlicesOf(heaviest, wholeName);
  if (rest !== null) slices.push(otherSectorsSliceOf(rest.weights, rest.total, wholeName));
  if (noSectorWeight !== null) slices.push(noSectorSliceOf(noSectorWeight, wholeName));
  return slices;
}

function donutSliceOf(sectorSlice: SectorSlice): DonutSlice {
  return { key: sectorSlice.key, amount: sectorSlice.weight.percentOfStocks, paintClass: sectorSlice.paint.fillClass };
}

/** The ring's hole: the slice pointed at — its percent over its name —, or else how many sectors there are. */
function RingCenter({ highlightedSlice, sectorCount }: { highlightedSlice: SectorSlice | null; sectorCount: number }) {
  if (highlightedSlice !== null) {
    return (
      <>
        <span className={RING_CENTER_FIGURE_CLASS}>{formatPercent(highlightedSlice.weight.percentOfStocks)}</span>
        <span className="line-clamp-2 text-caption text-muted">{highlightedSlice.name}</span>
      </>
    );
  }
  return (
    <>
      <span className={RING_CENTER_FIGURE_CLASS}>{sectorCount}</span>
      <span className="text-caption text-muted">{sectorCount === 1 ? 'sector' : 'sectors'}</span>
    </>
  );
}

/**
 * One row of the legend: swatch · name · percent · value. Pointing at it or reaching it with the keyboard highlights it
 * and its slice, and opens its Tip above the name.
 */
function LegendRow({
  slice,
  isHighlighted,
  onHighlight,
}: {
  slice: SectorSlice;
  isHighlighted: boolean;
  onHighlight: (key: string | null) => void;
}) {
  const tipId = useId();
  return (
    <li
      tabIndex={0}
      aria-describedby={tipId}
      onMouseEnter={() => onHighlight(slice.key)}
      onMouseLeave={() => onHighlight(null)}
      onFocus={() => onHighlight(slice.key)}
      onBlur={() => onHighlight(null)}
      className={`-mx-1.5 grid h-[30px] grid-cols-[10px_minmax(0,1fr)_48px_84px] items-center gap-x-2 rounded-sm px-1.5 text-[13px] ${
        isHighlighted ? 'bg-subtle' : ''
      }`}
    >
      <span className={`h-2.5 w-2.5 rounded-[2px] ${slice.paint.swatchClass}`} />
      <span className="relative min-w-0">
        <span className="block truncate">{slice.name}</span>
        <Tip id={tipId} text={slice.hint} isOpen={isHighlighted} />
      </span>
      <span className="text-right font-semibold">{formatPercent(slice.weight.percentOfStocks)}</span>
      <span className="text-right text-muted">{formatMoney(slice.weight.marketValue)}</span>
    </li>
  );
}

/**
 * The money in stocks by sector — the tab's: an investor's own on their tab —: a ring, and beside it a legend naming every
 * slice with its percent and value. Pointing at a slice or at its row highlights both and opens the row's Tip.
 */
export function SectorWeightsCard({
  tab,
  sectorWeights,
  noSectorWeight,
}: {
  tab: InvestmentsAndCash;
  sectorWeights: SectorWeight[];
  noSectorWeight: NoSectorWeight | null;
}) {
  const [highlightedKey, setHighlightedKey] = useState<string | null>(null);
  const slices = sectorSlicesOf(sectorWeights, noSectorWeight, tab.wholeName);
  const highlightedSlice = slices.find((slice) => slice.key === highlightedKey) ?? null;

  return (
    <WeightsCard title="By sector" caption={`Share of ${tab.stocksName}`}>
      <div className="flex items-start gap-6">
        <DonutChart slices={slices.map(donutSliceOf)} highlightedKey={highlightedKey} onHighlight={setHighlightedKey}>
          <RingCenter highlightedSlice={highlightedSlice} sectorCount={sectorWeights.length} />
        </DonutChart>
        <ul className="min-w-0 flex-1">
          {slices.map((slice) => (
            <LegendRow
              key={slice.key}
              slice={slice}
              isHighlighted={slice.key === highlightedKey}
              onHighlight={setHighlightedKey}
            />
          ))}
        </ul>
      </div>
    </WeightsCard>
  );
}
