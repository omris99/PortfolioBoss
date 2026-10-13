import { useState } from 'react';
import { formatMoney, formatPercent } from '../../lib/format';
import { DonutChart, type DonutSlice } from './DonutChart';
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

/** Outlined, like a holding with no signal: a sector not entered yet is unknown, not a sector of its own. */
const NO_SECTOR_PAINT: SlicePaint = {
  fillClass: 'fill-unknown-soft stroke-signal-none',
  swatchClass: 'bg-unknown-soft ring-1 ring-inset ring-signal-none',
};

/** One slice of the ring, with its row in the legend. */
interface SectorSlice {
  key: string;
  name: string;
  weight: Weight;
  /** What hovering its legend row adds to the row. */
  hint: string;
  paint: SlicePaint;
}

function coloredSectorSlicesOf(sectorWeights: SectorWeight[]): SectorSlice[] {
  return sectorWeights.map((sectorWeight, index) => ({
    key: `sector:${sectorWeight.sector}`,
    name: sectorWeight.sector,
    weight: sectorWeight,
    hint: hintOf([countText(sectorWeight.holdingCount, 'holding'), accountShareText(sectorWeight)]),
    paint: SECTOR_PAINTS[index],
  }));
}

function otherSectorsSliceOf(otherSectorWeights: SectorWeight[], total: Weight): SectorSlice {
  return {
    key: 'other-sectors',
    name: `Other (${countText(otherSectorWeights.length, 'sector')})`,
    weight: total,
    hint: hintOf([otherSectorWeights.map((sectorWeight) => sectorWeight.sector).join(', '), accountShareText(total)]),
    paint: OTHER_SECTORS_PAINT,
  };
}

function noSectorSliceOf(noSectorWeight: NoSectorWeight): SectorSlice {
  return {
    key: 'no-sector',
    name: 'No sector',
    weight: noSectorWeight,
    hint: hintOf([
      `${countText(noSectorWeight.holdingCount, 'holding')} without a sector yet: add it in Positions`,
      accountShareText(noSectorWeight),
    ]),
    paint: NO_SECTOR_PAINT,
  };
}

/** The heaviest sectors in their colors, then the others together, then the holdings with no sector. */
function sectorSlicesOf(sectorWeights: SectorWeight[], noSectorWeight: NoSectorWeight | null): SectorSlice[] {
  const { heaviest, rest } = heaviestWeightsOf(sectorWeights, SECTOR_PAINTS.length);
  const slices = coloredSectorSlicesOf(heaviest);
  if (rest !== null) slices.push(otherSectorsSliceOf(rest.weights, rest.total));
  if (noSectorWeight !== null) slices.push(noSectorSliceOf(noSectorWeight));
  return slices;
}

function donutSliceOf(sectorSlice: SectorSlice): DonutSlice {
  return { key: sectorSlice.key, amount: sectorSlice.weight.percentOfStocks, paintClass: sectorSlice.paint.fillClass };
}

/** The ring's hole: the slice pointed at, or else how many sectors there are. */
function RingCenter({ highlightedSlice, sectorCount }: { highlightedSlice: SectorSlice | null; sectorCount: number }) {
  if (highlightedSlice !== null) {
    return (
      <>
        <span className="text-figure">{formatPercent(highlightedSlice.weight.percentOfStocks)}</span>
        <span className="w-full truncate text-caption text-muted">{highlightedSlice.name}</span>
      </>
    );
  }
  return (
    <>
      <span className="text-figure-lg">{sectorCount}</span>
      <span className="text-caption text-muted">{sectorCount === 1 ? 'sector' : 'sectors'}</span>
    </>
  );
}

function LegendRow({
  slice,
  isHighlighted,
  onHighlight,
}: {
  slice: SectorSlice;
  isHighlighted: boolean;
  onHighlight: (key: string | null) => void;
}) {
  return (
    <li
      title={slice.hint}
      onMouseEnter={() => onHighlight(slice.key)}
      onMouseLeave={() => onHighlight(null)}
      className={`grid grid-cols-[10px_minmax(0,1fr)_52px_96px] items-center gap-x-3 rounded px-2 py-1.5 transition-colors ${
        isHighlighted ? 'bg-subtle' : ''
      }`}
    >
      <span className={`h-2.5 w-2.5 rounded-sm ${slice.paint.swatchClass}`} />
      <span className="truncate">{slice.name}</span>
      <span className="text-right font-semibold">{formatPercent(slice.weight.percentOfStocks)}</span>
      <span className="text-right text-caption text-muted">{formatMoney(slice.weight.marketValue)}</span>
    </li>
  );
}

/**
 * The money in stocks by sector: a ring, and beside it a legend naming every slice with its percent and value. Pointing
 * at a slice or at its row highlights both.
 */
export function SectorWeightsCard({
  sectorWeights,
  noSectorWeight,
}: {
  sectorWeights: SectorWeight[];
  noSectorWeight: NoSectorWeight | null;
}) {
  const [highlightedKey, setHighlightedKey] = useState<string | null>(null);
  const slices = sectorSlicesOf(sectorWeights, noSectorWeight);
  const highlightedSlice = slices.find((slice) => slice.key === highlightedKey) ?? null;

  return (
    <WeightsCard title="By sector" caption="Share of the money in stocks">
      <div className="flex flex-1 items-center gap-6 px-5 pb-5">
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
