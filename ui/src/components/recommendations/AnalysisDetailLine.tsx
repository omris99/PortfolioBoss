import { Fragment, type ReactNode } from 'react';
import { EMPTY_VALUE, formatSignedPercentOneDecimal } from '../../lib/format';
import { RATING_TONES, textClassOf, TREND_TONES } from '../../lib/signalColors';
import type { HotStock } from '../../types/recommendations';
import { siteOf } from '../AnalysisPanel';
import { formatPoints, NEWS_SCORE_HINT, pointsColorClass } from './NewsScore';
import { RiskSigns } from './RiskSignTag';
import { earningsText, TimingNote } from './TimingNote';

/**
 * The line under an analyzed row of the Hot list: its risk signs, the analysts, the next earnings and the news score,
 * each named in the line itself — "Risk signs [Expensive] · Analysts BUY +2.4% IMPROVING · Earnings 2026-11-04 · News
 * +2" —, and under it why the price moves, in full, when the news says. The columns above don't move.
 */

/** "BUY +18.1% IMPROVING": the consensus, how far its target is from the price, and the trend — each in its tone. */
function AnalystsPart({ stock }: { stock: HotStock }) {
  const analysis = stock.analysis;
  const consensus = analysis?.consensus ?? null;
  if (analysis === null || (consensus === null && analysis.analystTrend === null)) return <>{EMPTY_VALUE}</>;
  return (
    <span className="inline-flex gap-x-1.5 font-medium">
      {consensus?.rating != null && (
        <span className={textClassOf(consensus.rating, RATING_TONES)}>{consensus.rating.replace('_', ' ')}</span>
      )}
      {consensus?.targetUpsidePercent != null && (
        <span title="How far the consensus target is from the last close">
          {formatSignedPercentOneDecimal(consensus.targetUpsidePercent)}
        </span>
      )}
      {analysis.analystTrend !== null && (
        <span className={textClassOf(analysis.analystTrend, TREND_TONES)}>{analysis.analystTrend}</span>
      )}
    </span>
  );
}

/** The next report's date, outlined with the calendar when it is within 14 days of the scan — the timing note. */
function EarningsPart({ stock }: { stock: HotStock }) {
  if (stock.nextEarningsDate === null) {
    return <span title="None in the next four months, or Finnhub gave none">{EMPTY_VALUE}</span>;
  }
  if (stock.reportsWithin14Days) return <TimingNote earningsDate={stock.nextEarningsDate} showsDateOnly />;
  return <span title={earningsText(stock.nextEarningsDate)}>{stock.nextEarningsDate.date}</span>;
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
          { name: 'Risk signs', value: <RiskSigns stock={stock} showFigures={false} /> },
          { name: 'Analysts', value: <AnalystsPart stock={stock} /> },
          { name: 'Earnings', value: <EarningsPart stock={stock} /> },
          { name: 'News', value: <NewsPart stock={stock} /> },
        ]}
      />
      <WhyItMovesLine stock={stock} />
    </div>
  );
}
