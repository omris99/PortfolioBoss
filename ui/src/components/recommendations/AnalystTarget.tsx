import {
  EMPTY_VALUE,
  formatDate,
  formatMoney,
  formatSignedPercentOneDecimal,
  profitLossColorClass,
} from '../../lib/format';
import { EXPLAINED_CLASS } from '../../lib/styles';
import type { AnalystPriceTarget, AnalystTarget } from '../../types/recommendations';
import { SubHeading } from '../AnalysisPanel';

/**
 * The analysts' target from Dow Jones's headlines (RECOMMENDATIONS_TODO.md, decision 14): known for every scanned stock
 * before anything is paid for, and the one the recommendation's rule counts — the MarketBeat consensus of the analysis
 * stays beside it in the drawer. Every figure is the API's.
 */

/** The rule in words — a copy of the API's numbers, for the words only. */
export const TARGET_RULE_TEXT =
  'A pick needs the median of 3 firms’ targets or more, at least 15% above the price — each firm’s latest of the ' +
  'half year, from Dow Jones’s headlines.';

function firmCountText(firmCount: number): string {
  if (firmCount === 0) return 'no firm';
  return firmCount === 1 ? '1 firm' : `${firmCount} firms`;
}

/** On hover: what the figure is — or why there is none. */
export function analystTargetHint(analystTarget: AnalystTarget): string {
  if (analystTarget.medianTarget === null) {
    return (
      `${firmCountText(analystTarget.firmCount)} gave a target in Dow Jones’s headlines of the half year: fewer than ` +
      '3 is an opinion, not a target.'
    );
  }
  return (
    `The median of ${analystTarget.firmCount} firms’ latest targets in Dow Jones’s headlines of the half year: ` +
    `${formatMoney(analystTarget.medianTarget)}, against the last close.`
  );
}

/** "+27.1%" in its tone, or "—" without a target; the hint says what it is. */
export function TargetUpside({ analystTarget }: { analystTarget: AnalystTarget }) {
  const upsidePercent = analystTarget.upsidePercent;
  const toneClass = profitLossColorClass(upsidePercent);
  return (
    <span title={analystTargetHint(analystTarget)} className={`${EXPLAINED_CLASS} ${toneClass}`}>
      {upsidePercent === null ? EMPTY_VALUE : formatSignedPercentOneDecimal(upsidePercent)}
    </span>
  );
}

/** A list's cell: the upside, and how many firms under it. */
export function TargetCell({ analystTarget }: { analystTarget: AnalystTarget }) {
  return (
    <>
      <TargetUpside analystTarget={analystTarget} />
      <div className="mt-1 text-caption text-muted">{firmCountText(analystTarget.firmCount)}</div>
    </>
  );
}

/** "2026-10-12 · Needham · 450.00", and the target it replaced when the headline names one. */
function FirmTargetRow({ firmTarget }: { firmTarget: AnalystPriceTarget }) {
  return (
    <li title={firmTarget.headline} className="grid grid-cols-[84px_1fr_auto] gap-3 border-b py-2 text-[13px]">
      <span className="text-muted">{formatDate(firmTarget.publishedAt.slice(0, 10))}</span>
      <span className="min-w-0 truncate font-medium">{firmTarget.firm}</span>
      <span className="text-right">
        {firmTarget.previousPriceTarget !== null && (
          <span className="text-muted">{formatMoney(firmTarget.previousPriceTarget)} → </span>
        )}
        {formatMoney(firmTarget.priceTarget)}
      </span>
    </li>
  );
}

/**
 * The drawer's "Analysts & news" tab, first: the median target and its upside, the rule, and each firm's latest target,
 * the newest first — the headline on hover.
 */
export function AnalystTargetSection({ analystTarget }: { analystTarget: AnalystTarget }) {
  return (
    <section className="mb-5">
      <SubHeading>Analysts’ target · Dow Jones</SubHeading>
      <p className="mb-1">
        {analystTarget.medianTarget === null ? (
          <span className="text-unknown">No target: {analystTargetHint(analystTarget)}</span>
        ) : (
          <>
            Median {formatMoney(analystTarget.medianTarget)} <TargetUpside analystTarget={analystTarget} />
            <span className="text-muted"> · {firmCountText(analystTarget.firmCount)}</span>
          </>
        )}
      </p>
      <p className="mb-2 text-caption text-faint">{TARGET_RULE_TEXT}</p>
      {analystTarget.latestTargetOfEachFirm.length > 0 && (
        <ul className="border-t">
          {analystTarget.latestTargetOfEachFirm.map((firmTarget) => (
            <FirmTargetRow key={`${firmTarget.firm}-${firmTarget.publishedAt}`} firmTarget={firmTarget} />
          ))}
        </ul>
      )}
    </section>
  );
}
