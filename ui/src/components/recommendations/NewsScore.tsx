import type { ReactNode } from 'react';
import type { NewsEventType } from '../../types/portfolio';
import type { HotStock, WeighedAnalystMove, WeighedNewsEvent } from '../../types/recommendations';
import { ratingChangeOf, siteOf, SourceLink, SubHeading, targetChangeOf } from '../AnalysisPanel';

/**
 * A scanned stock's news score (RECOMMENDATIONS_TODO.md, decision 13) and what it is made of: each event Claude found in
 * the week's news and each analyst's upgrade or downgrade that counts, with the points the API gave it. Whether a part
 * is good or bad, and how much, is the API's — this only shows it.
 */

/** Each kind of event, in words. */
const EVENT_NAMES: Record<NewsEventType, string> = {
  PARTNERSHIP: 'Partnership',
  MAJOR_CONTRACT: 'Major contract',
  PRODUCT_LAUNCH: 'Product launch',
  GUIDANCE_RAISED: 'Guidance raised',
  EARNINGS_BEAT: 'Earnings beat',
  REGULATORY_APPROVAL: 'Regulatory approval',
  GUIDANCE_CUT: 'Guidance cut',
  EARNINGS_MISS: 'Earnings miss',
  SHARE_OFFERING: 'Share offering',
  LAWSUIT_OR_REGULATION: 'Lawsuit or regulation',
  NOT_IN_THE_LIST: 'Not in the list',
};

const NO_PARTS_TEXT =
  'No event in the week’s news, and no analyst’s upgrade or downgrade with a price target in the last 30 days.';

export const NEWS_SCORE_HINT =
  'The week’s events in the news and the analysts’ upgrades and downgrades with a price target, each weighed by ' +
  'rules in code. Above 0 the good outweighs the bad.';

/** "+3", "−2", "0". */
export function formatPoints(points: number): string {
  if (points > 0) return `+${points}`;
  if (points < 0) return `−${Math.abs(points)}`;
  return '0';
}

/** Good news in the positive tone, bad in the warning one, nothing in grey — the tones of the news's sentiment. */
export function pointsColorClass(points: number): string {
  if (points > 0) return 'text-positive';
  if (points < 0) return 'text-negative';
  return 'text-muted';
}

function Points({ points }: { points: number }) {
  return <span className={`font-semibold tabular-nums ${pointsColorClass(points)}`}>{formatPoints(points)}</span>;
}

/** "Partnership · high: HOTA teams up with … · examplewire.com". */
function EventPart({ weighedEvent }: { weighedEvent: WeighedNewsEvent }) {
  const { event } = weighedEvent;
  const isNotInTheList = event.type === 'NOT_IN_THE_LIST';
  return (
    <>
      <span
        title={isNotInTheList ? 'No kind of event fits it: it weighs nothing.' : undefined}
        className="font-medium"
      >
        {EVENT_NAMES[event.type]}
        {event.magnitude !== null && ` · ${event.magnitude.toLowerCase()}`}
      </span>
      : {event.summary}{' '}
      <SourceLink url={event.url} title={event.title}>
        {siteOf(event.url)}
      </SourceLink>
    </>
  );
}

/** "Upgraded by Some Firm, 2026-10-20: Hold → Buy · target 90.00 → 120.00 · examplewire.com". */
function AnalystMovePart({ weighedMove }: { weighedMove: WeighedAnalystMove }) {
  const { action } = weighedMove;
  const details = [ratingChangeOf(action), targetChangeOf(action)].filter((detail) => detail !== null).join(' · ');
  return (
    <>
      <span className="font-medium">
        {action.action === 'UPGRADE' ? 'Upgraded' : 'Downgraded'} by {action.firm}
      </span>
      , {action.date}
      {details !== '' && `: ${details}`}{' '}
      <SourceLink url={action.url}>{siteOf(action.url)}</SourceLink>
    </>
  );
}

function PartRow({ points, children }: { points: number; children: ReactNode }) {
  return (
    <li className="grid grid-cols-[28px_1fr] gap-2 py-1">
      <Points points={points} />
      <span className="min-w-0">{children}</span>
    </li>
  );
}

/** Every part of the score, events first, each with its points; one line when there is none. */
export function NewsScoreParts({ stock }: { stock: HotStock }) {
  if (stock.weighedNewsEvents.length === 0 && stock.countedAnalystMoves.length === 0) {
    return <p className="text-muted">{NO_PARTS_TEXT}</p>;
  }
  return (
    <ul>
      {stock.weighedNewsEvents.map((weighedEvent, index) => (
        <PartRow key={`event-${index}`} points={weighedEvent.points}>
          <EventPart weighedEvent={weighedEvent} />
        </PartRow>
      ))}
      {stock.countedAnalystMoves.map((weighedMove, index) => (
        <PartRow key={`move-${index}`} points={weighedMove.points}>
          <AnalystMovePart weighedMove={weighedMove} />
        </PartRow>
      ))}
    </ul>
  );
}

/** The drawer's "News score +5" section, under the news: the total, then its parts. Nothing while never analyzed. */
export function NewsScoreSection({ stock }: { stock: HotStock }) {
  if (stock.newsScore === null) return null;
  return (
    <>
      <SubHeading>
        <span title={NEWS_SCORE_HINT}>
          News score <Points points={stock.newsScore} />
        </span>
      </SubHeading>
      <NewsScoreParts stock={stock} />
    </>
  );
}
