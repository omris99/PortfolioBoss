import { Fragment, type ReactNode } from 'react';
import { EMPTY_VALUE } from '../../lib/format';
import { RATING_TONES, textClassOf, TREND_TONES } from '../../lib/signalColors';
import type { HotStock } from '../../types/recommendations';
import { siteOf } from '../AnalysisPanel';
import { NextEarningsDate } from '../companyInfo/TimingNote';
import { CandidateRiskSigns } from './CandidateRiskSigns';
import { formatPoints, NEWS_SCORE_HINT, pointsColorClass } from './NewsScore';

/**
 * The line under an analyzed row of the Hot list: its risk signs, the analysts, the next earnings and the news score,
 * each named in the line itself — "Risk signs [Expensive] · Analysts BUY IMPROVING · Earnings 2026-11-04 · News +2" —,
 * and under it why the price moves, in full, when the news says. The columns above don't move.
 */

/**
 * "BUY IMPROVING": the consensus and the trend, each in its tone. The target's upside is the row's own column, from
 * Dow Jones — the one the rule counts —, so the analysis's MarketBeat figure stays in the drawer and not here.
 */
function AnalystsPart({ stock }: { stock: HotStock }) {
  const analysis = stock.analysis;
  const consensus = analysis?.consensus ?? null;
  if (analysis === null || (consensus?.rating == null && analysis.analystTrend === null)) return <>{EMPTY_VALUE}</>;
  return (
    <span className="inline-flex gap-x-1.5 font-medium">
      {consensus?.rating != null && (
        <span className={textClassOf(consensus.rating, RATING_TONES)}>{consensus.rating.replace('_', ' ')}</span>
      )}
      {analysis.analystTrend !== null && (
        <span className={textClassOf(analysis.analystTrend, TREND_TONES)}>{analysis.analystTrend}</span>
      )}
    </span>
  );
}

function NewsPart({ stock }: { stock: HotStock }) {
  if (stock.newsScore === null) return <>{EMPTY_VALUE}</>;
  return (
    <span title={NEWS_SCORE_HINT} className={`font-semibold ${pointsColorClass(stock.newsScore)}`}>
      {formatPoints(stock.newsScore)}
    </span>
  );
}

interface NamedPart {
  name: string;
  value: ReactNode;
}

/** The parts in a row, each after its name, with "·" between them; the row wraps when the table is narrow. */
function NamedParts({ parts }: { parts: NamedPart[] }) {
  return (
    <div className="flex flex-wrap items-center gap-x-1.5 gap-y-1">
      {parts.map((part, index) => (
        <Fragment key={part.name}>
          {index > 0 && (
            <span aria-hidden="true" className="px-1 text-faint">
              ·
            </span>
          )}
          <span className="text-muted">{part.name}</span>
          {part.value}
        </Fragment>
      ))}
    </div>
  );
}

/** Why the price moves, from the week's news, in full — its source after it, the result's title on hover. */
function WhyItMovesLine({ stock }: { stock: HotStock }) {
  const moveReason = stock.analysis?.moveReason ?? null;
  if (moveReason === null) return null;
  return (
    <p title={moveReason.title} className="max-w-[880px]">
      <span className="text-muted">Why it moves: </span>
      {moveReason.reason} <span className="text-muted">{siteOf(moveReason.url)}</span>
    </p>
  );
}

/** Only for a stock with an analysis: before it, the row has no line under it. */
export function AnalysisDetailLine({ stock }: { stock: HotStock }) {
  return (
    <div className="flex flex-col gap-1 text-caption">
      <NamedParts
        parts={[
          { name: 'Risk signs', value: <CandidateRiskSigns stock={stock} showFigures={false} /> },
          { name: 'Analysts', value: <AnalystsPart stock={stock} /> },
          { name: 'Earnings', value: <NextEarningsDate companyInfo={stock.companyInfo} markPlacement="beside" /> },
          { name: 'News', value: <NewsPart stock={stock} /> },
        ]}
      />
      <WhyItMovesLine stock={stock} />
    </div>
  );
}
