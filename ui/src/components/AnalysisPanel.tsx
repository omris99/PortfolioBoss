import { Fragment, type ReactNode } from 'react';
import { ExternalLink } from 'lucide-react';
import { formatDateTime, formatMoney, formatSignedPercent, profitLossColorClass } from '../lib/format';
import { ACTION_TONES, labelClassOf, RATING_TONES, SENTIMENT_TONES, TREND_TONES } from '../lib/signalColors';
import { CHIP_CLASS, EXPLAINED_CLASS } from '../lib/styles';
import type {
  AnalystAction,
  AnalystActionType,
  AnalystConsensus,
  AnalystRating,
  AnalystTrend,
  EstimatedAnalysisCost,
  Headline,
  Holding,
  Sentiment,
  SourceConsensus,
  StockAnalysis,
} from '../types/portfolio';
import { AnalyzeHoldingButton } from './AnalyzeControls';
import { HelpPopover } from './HelpPopover';
import { noTrendReason, SignalLegend, targetMovesText } from './Signal';

/** What an analyst firm did, in plain words. */
const ACTION_NAMES: Record<AnalystActionType, string> = {
  UPGRADE: 'UPGRADED',
  DOWNGRADE: 'DOWNGRADED',
  INITIATE: 'NEW COVERAGE',
  REITERATE: 'RATING KEPT',
  TARGET_RAISED: 'TARGET RAISED',
  TARGET_LOWERED: 'TARGET LOWERED',
};

// ── chips, shared with the drawer's Summary ─────────────────────────────────────────────────────

/** "STRONG_BUY" → "STRONG BUY": the API's names, readable. */
function labelTextOf(value: string): string {
  return value.replace(/_/g, ' ');
}

export function RatingChip({ rating }: { rating: AnalystRating | null }) {
  return (
    <span className={`${CHIP_CLASS} ${labelClassOf(rating, RATING_TONES)}`}>
      {rating === null ? 'NO RATING' : labelTextOf(rating)}
    </span>
  );
}

export function TrendChip({ trend }: { trend: AnalystTrend | null }) {
  return <span className={`${CHIP_CLASS} ${labelClassOf(trend, TREND_TONES)}`}>{trend ?? 'NO TREND'}</span>;
}

export function SentimentChip({ sentiment }: { sentiment: Sentiment | null }) {
  return (
    <span className={`${CHIP_CLASS} ${labelClassOf(sentiment, SENTIMENT_TONES)}`}>{sentiment ?? 'UNKNOWN'}</span>
  );
}

/** "target 340.02 (+2.04%)": the chosen source's target against IB's price, the upside in its colour. */
export function TargetWithUpside({ consensus }: { consensus: AnalystConsensus }) {
  if (consensus.averageTarget === null) return null;
  return (
    <span>
      target {formatMoney(consensus.averageTarget)}
      {consensus.targetUpsidePercent !== null && (
        <span className={profitLossColorClass(consensus.targetUpsidePercent)}>
          {' '}
          ({formatSignedPercent(consensus.targetUpsidePercent)})
        </span>
      )}
    </span>
  );
}

// ── sources ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The address when it is a web page, `null` otherwise. It comes from search results, which are untrusted text, so
 * anything but http(s) — a `javascript:` address, say — is shown as text, never as a link.
 */
function webAddressOrNull(url: string): string | null {
  try {
    const protocol = new URL(url).protocol;
    return protocol === 'https:' || protocol === 'http:' ? url : null;
  } catch {
    return null;
  }
}

/** "marketbeat.com" for https://www.marketbeat.com/stocks/NASDAQ/AAPL/forecast; the address itself if it can't be read. */
function siteOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url;
  }
}

/** Opens the source in a new tab, with the external-link icon; plain text when the address is not a web page. */
function SourceLink({ url, children }: { url: string; children: ReactNode }) {
  const webAddress = webAddressOrNull(url);
  if (webAddress === null) return <span>{children}</span>;
  return (
    <a
      href={webAddress}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-[3px] text-link underline-offset-2 hover:underline"
    >
      {children}
      <ExternalLink size={12} className="shrink-0" />
    </a>
  );
}

const COPY_DATE_HINT =
  'The date the search engine gives its copy of this page, which is what was read — not always exact. The page may ' +
  'have changed since: newer analyst actions may be missing, and older rows may have dropped off it.';

/** "copy of 2026-09-18": how old the search engine's copy of a source may be (AI_ANALYSIS_TODO.md, decision 20). */
function CopyDate({ date }: { date: string | null }) {
  return (
    <span title={COPY_DATE_HINT} className={`text-caption text-faint ${EXPLAINED_CLASS}`}>
      {date === null ? 'copy date unknown' : `copy of ${date}`}
    </span>
  );
}

/** Its parts in a row with "·" between them; a missing part (`null`) leaves no double "·". */
function DottedLine({ parts, className = '' }: { parts: ReactNode[]; className?: string }) {
  const presentParts = parts.filter((part) => part !== null);
  return (
    <div className={`mb-2 flex flex-wrap items-center gap-1.5 ${className}`}>
      {presentParts.map((part, index) => (
        <Fragment key={index}>
          {index > 0 && <span className="text-faint">·</span>}
          {part}
        </Fragment>
      ))}
    </div>
  );
}

function SubHeading({ children }: { children: ReactNode }) {
  return <h3 className="mb-2 mt-5 text-[13px] font-semibold leading-5 first:mt-0">{children}</h3>;
}

// ── analysts ────────────────────────────────────────────────────────────────────────────────────

/** The date of the chosen source's search result, as Claude copied it among every source's. */
function publishedDateOf(sourceUrl: string, sources: SourceConsensus[]): string | null {
  return sources.find((source) => source.sourceUrl === sourceUrl)?.publishedDate ?? null;
}

/**
 * "BUY · 42 analysts · target 340.02 (+2.04%) · marketbeat.com · copy of 2026-10-01": the consensus the API chose, its
 * source and how old the search engine's copy of it is.
 */
function ConsensusLine({ consensus, sources }: { consensus: AnalystConsensus; sources: SourceConsensus[] }) {
  return (
    <DottedLine
      parts={[
        <RatingChip rating={consensus.rating} />,
        consensus.analystCount === null ? null : <span>{consensus.analystCount} analysts</span>,
        consensus.averageTarget === null ? null : <TargetWithUpside consensus={consensus} />,
        <span className="inline-flex items-center gap-1.5">
          <SourceLink url={consensus.sourceUrl}>{siteOf(consensus.sourceUrl)}</SourceLink>
          <CopyDate date={publishedDateOf(consensus.sourceUrl, sources)} />
        </span>,
      ]}
    />
  );
}

/** "financhill.com (copy of 2026-10-04): BUY, 48 analysts, target 328.22" — one line of the hover over the spread. */
function sourceSummaryOf(source: SourceConsensus): string {
  const figures = [
    source.ratingLabel === null ? null : labelTextOf(source.ratingLabel),
    source.analystCount === null ? null : `${source.analystCount} analysts`,
    source.averageTarget === null ? null : `target ${formatMoney(source.averageTarget)}`,
  ].filter((figure) => figure !== null);
  const copyDate = source.publishedDate === null ? 'copy date unknown' : `copy of ${source.publishedDate}`;
  return `${siteOf(source.sourceUrl)} (${copyDate}): ${figures.length === 0 ? 'no figures' : figures.join(', ')}`;
}

/**
 * "Targets 323.86–340.02 across 4 sources", every source's figures on hover: one target that looks exact says less
 * than the spread (AI_ANALYSIS_TODO.md, decision 16). Nothing with fewer than two targets.
 */
function TargetSpread({ consensus, sources }: { consensus: AnalystConsensus; sources: SourceConsensus[] }) {
  if (consensus.sourceCount < 2) return null;
  return (
    <p className="mb-2">
      <span title={sources.map(sourceSummaryOf).join('\n')} className={EXPLAINED_CLASS}>
        Targets {formatMoney(consensus.targetLow)}–{formatMoney(consensus.targetHigh)} across {consensus.sourceCount}{' '}
        sources
      </span>
    </p>
  );
}

/**
 * "Trend IMPROVING · 3 targets raised, 0 lowered": the trend the API worked out from the price targets of the actions
 * listed under it. Without one, says why — no action at all, or ratings only.
 */
function TrendLine({ analysis }: { analysis: StockAnalysis }) {
  return (
    <div className="mb-2 flex flex-wrap items-center gap-1.5">
      <span className="text-muted">Trend</span>
      <TrendChip trend={analysis.analystTrend} />
      {analysis.analystTrend === null ? (
        <span className="text-muted">{noTrendReason(analysis)}</span>
      ) : (
        <span title="Only price targets count, among the actions below: a rating without a target moves nothing">
          {targetMovesText(analysis)}
        </span>
      )}
    </div>
  );
}

/** "Neutral → Hold", or the rating alone when it stayed the same; `null` without one. In the firm's own words. */
function ratingChangeOf(action: AnalystAction): string | null {
  const { fromRating, toRating } = action;
  if (toRating === null) return fromRating;
  if (fromRating === null || fromRating.toLowerCase() === toRating.toLowerCase()) return toRating;
  return `${fromRating} → ${toRating}`;
}

/** "target 20.00 → 15.00", or "target 15.00"; `null` without one. */
function targetChangeOf(action: AnalystAction): string | null {
  if (action.priceTarget === null) return null;
  if (action.previousPriceTarget === null) return `target ${formatMoney(action.priceTarget)}`;
  return `target ${formatMoney(action.previousPriceTarget)} → ${formatMoney(action.priceTarget)}`;
}

/** Two lines: "2026-10-06 · Goldman Sachs · TARGET LOWERED", then "Hold · target 20.00 → 15.00 · moomoo.com · copy of…". */
function ActionRow({ action }: { action: AnalystAction }) {
  const ratingChange = ratingChangeOf(action);
  const targetChange = targetChangeOf(action);
  return (
    <li className="border-b py-2.5">
      <div className="flex items-center gap-3">
        <span className="text-muted">{action.date}</span>
        <span className="min-w-0 flex-1 font-medium">{action.firm}</span>
        <span className={`${CHIP_CLASS} ${labelClassOf(action.action, ACTION_TONES)}`}>{ACTION_NAMES[action.action]}</span>
      </div>
      <div className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 pl-[92px] text-[13px] text-muted">
        {ratingChange !== null && <span>{ratingChange}</span>}
        {targetChange !== null && <span>{targetChange}</span>}
        <SourceLink url={action.url}>{siteOf(action.url)}</SourceLink>
        <CopyDate date={action.sourcePublishedDate} />
      </div>
    </li>
  );
}

/**
 * The newest action first: Claude lists them in the order it found them, not always by date. 'yyyy-MM-dd' sorts
 * correctly as text, and actions of the same day keep their order.
 */
function newestActionsFirst(actions: AnalystAction[]): AnalystAction[] {
  return [...actions].sort((first, second) => second.date.localeCompare(first.date));
}

function AnalystsSection({ analysis }: { analysis: StockAnalysis }) {
  return (
    <>
      <SubHeading>Analysts</SubHeading>
      {analysis.consensus === null ? (
        <p className="mb-2 text-unknown">No analyst consensus in the search results.</p>
      ) : (
        <>
          <ConsensusLine consensus={analysis.consensus} sources={analysis.consensusBySource} />
          <TargetSpread consensus={analysis.consensus} sources={analysis.consensusBySource} />
        </>
      )}
      <TrendLine analysis={analysis} />
      {analysis.recentActions.length > 0 && (
        <ul className="mt-3 border-t">
          {newestActionsFirst(analysis.recentActions).map((action, index) => (
            <ActionRow key={`${action.date}-${action.firm}-${index}`} action={action} />
          ))}
        </ul>
      )}
    </>
  );
}

// ── news ────────────────────────────────────────────────────────────────────────────────────────

/** "2026-10-08 · Zacks.com · Apple Rises 24% YTD…", the headline linking to the article. */
function HeadlineRow({ headline }: { headline: Headline }) {
  const webAddress = webAddressOrNull(headline.url);
  return (
    <li className="grid grid-cols-[84px_104px_1fr] gap-2 border-t py-2 text-[13px]">
      <span className="text-muted">{headline.date ?? ''}</span>
      <span className="text-muted">{headline.source}</span>
      {webAddress === null ? (
        <span>{headline.title}</span>
      ) : (
        <a href={webAddress} target="_blank" rel="noopener noreferrer" className="text-link underline-offset-2 hover:underline">
          {headline.title}
          <ExternalLink size={12} className="ml-1 inline-block align-[-1px]" />
        </a>
      )}
    </li>
  );
}

function NewsSection({ analysis }: { analysis: StockAnalysis }) {
  return (
    <>
      <SubHeading>News</SubHeading>
      <div className="mb-2 flex items-start gap-1.5">
        <SentimentChip sentiment={analysis.sentiment} />
        <span>{analysis.sentimentReason ?? 'No recent news in the search results.'}</span>
      </div>
      {analysis.headlines.length > 0 ? (
        <ul className="mt-2">
          {analysis.headlines.map((headline, index) => (
            <HeadlineRow key={`${headline.url}-${index}`} headline={headline} />
          ))}
        </ul>
      ) : (
        <p className="text-muted">No headlines in the search results.</p>
      )}
    </>
  );
}

// ── the tab ─────────────────────────────────────────────────────────────────────────────────────

/**
 * The drawer's "Analysts & news" tab: when and by which model it was analyzed, the analysts' consensus the API chose
 * with the spread of the sources, their trend and latest actions, and the news — every fact linked to its source —
 * and a button to analyze this stock again (open holdings only: the API refuses a closed one).
 */
export function AnalysisPanel({
  holding,
  estimatedAnalysisCost,
  onDataChanged,
}: {
  holding: Holding;
  estimatedAnalysisCost: EstimatedAnalysisCost | null;
  onDataChanged: () => Promise<void>;
}) {
  const analysis = holding.analysis;
  const analyzeButton = holding.status === 'OPEN' && (
    <AnalyzeHoldingButton
      holdingId={holding.id}
      symbol={holding.symbol}
      estimatedAnalysisCost={estimatedAnalysisCost}
      onDataChanged={onDataChanged}
    />
  );

  if (analysis === null) {
    return (
      <div className="py-6 text-muted">
        <p className="mb-3">
          {holding.status === 'OPEN'
            ? 'Not analyzed yet. Analyze searches the web for its analyst ratings and news, and Claude reads what it finds.'
            : 'Not analyzed: the analysis runs on open holdings only.'}
        </p>
        {analyzeButton}
      </div>
    );
  }
  return (
    <div>
      <div className="mb-4 flex min-h-7 flex-wrap items-center gap-2">
        <span className="text-muted">
          Analyzed {formatDateTime(analysis.analyzedAt)} by {analysis.model}
        </span>
        <span className="ml-auto inline-flex items-center gap-1">
          {analyzeButton}
          <HelpPopover label="What the dot’s colors mean">
            <SignalLegend />
          </HelpPopover>
        </span>
      </div>
      <AnalystsSection analysis={analysis} />
      <NewsSection analysis={analysis} />
    </div>
  );
}
