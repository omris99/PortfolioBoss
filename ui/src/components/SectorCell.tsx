import { EMPTY_VALUE } from '../lib/format';
import type { Holding } from '../types/portfolio';

/** The `<datalist>` of the sectors known so far, rendered once by the app, so each is offered as you type. */
export const SECTOR_OPTIONS_LIST_ID = 'sector-options';

/**
 * Every sector so far — IB's on a holding, or typed on a manual position — once, A→Z: offered wherever a manual
 * position's sector is typed, so that it is spelled the way IB spells the holdings'.
 */
export function SectorOptionsList({ sectors }: { sectors: (string | null)[] }) {
  const distinctSectors = [...new Set(sectors.filter((sector) => sector !== null))].sort((firstSector, secondSector) =>
    firstSector.localeCompare(secondSector),
  );
  return (
    <datalist id={SECTOR_OPTIONS_LIST_ID}>
      {distinctSectors.map((sector) => (
        <option key={sector} value={sector} />
      ))}
    </datalist>
  );
}

/**
 * A holding's sector and industry in the positions table: IB's two words with every sync (MY_HOLDINGS_TODO.md,
 * decision 11), never typed by hand — "Technology", then "Semiconductors" in muted, as the Recommendations lists show a
 * scanned stock's. "—" when IB gives neither: an ETF gets none.
 */
export function SectorCell({ holding }: { holding: Holding }) {
  if (holding.sector === null && holding.industry === null) {
    return <span className="text-muted">{EMPTY_VALUE}</span>;
  }
  return (
    <>
      <div>{holding.sector ?? EMPTY_VALUE}</div>
      {holding.industry !== null && <div className="text-muted">{holding.industry}</div>}
    </>
  );
}

/** The same two words on one line, for the holding's drawer: "Technology / Semiconductors". */
export function sectorAndIndustryText(holding: Holding): string {
  const words = [holding.sector, holding.industry].filter((word) => word !== null);
  return words.length === 0 ? EMPTY_VALUE : words.join(' / ');
}
