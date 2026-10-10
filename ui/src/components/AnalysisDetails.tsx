import { Fragment, type ReactNode } from 'react';
import { ExternalLink } from 'lucide-react';
import { formatMoney, formatSignedPercent } from '../lib/format';
import { ACTION_TONES, labelClassOf, RATING_TONES, SENTIMENT_TONES, TREND_TONES } from '../lib/signalColors';
import type {
  AnalystAction,
  AnalystActionType,
  AnalystConsensus,
  Headline,
  Holding,
  SourceConsensus,
  StockAnalysis,
} from '../types/portfolio';
import { AnalyzeButton } from './AnalyzeButton';
import { analyzedAtText, noTrendReason, SIGNAL_MEANINGS, SignalDot, targetMovesText } from './Signal';

/** What an analyst firm did, in plain words. */
const ACTION_NAMES: Record<AnalystActionType, string> = {
  UPGRADE: 'UPGRADED',
  DOWNGRADE: 'DOWNGRADED',
  INITIATE: 'NEW COVERAGE',
  REITERATE: 'RATING KEPT',
  TARGET_RAISED: 'TARGET RAISED',
  TARGET_LOWERED: 'TARGET LOWERED',
};

// ── small pieces ────────────────────────────────────────────────────────────────────────────────

/** "STRONG_BUY" → "STRONG BUY": the API's names, readable. */
function labelTextOf(value: string): string {
  return value.replace(/_/g, ' ');
}

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

/** Opens the source in a new tab; plain text when the address is not a web page. */
function SourceLink({ url, children }: { url: string; children: ReactNode }) {
  const webAddress = webAddressOrNull(url);
  if (webAddress === null) return <span>{children}</span>;
  return (
    <a
      href={webAddress}
      target="_blank"
      rel="noopener noreferrer"
      className="inline-flex items-center gap-0.5 text-sky-400 transition-colors hover:text-sky-300 hover:underline"
    >
      {children}
      <ExternalLink size={10} className="shrink-0" />
    </a>
  );
}

const COPY_DATE_HINT =
  'The date the search engine gives its copy of this page, which is what was read — not always exact. The page may ' +
  'have changed since: newer analyst actions may be missing, and older rows may have dropped off it.';

/** "copy of 2026-09-18": how old the search engine's copy of a source may be (AI_ANALYSIS_TODO.md, decision 20). */
function CopyDate({ date }: { date: string | null }) {
  return (
    <span title={COPY_DATE_HINT} className="text-slate-500">
      {date === null ? 'copy date unknown' : `copy of ${date}`}
    </span>
  );
}

/** A small label in a colour: a rating, a trend, an action, a sentiment. */
function ColoredLabel({ colorClass, children }: { colorClass: string; children: ReactNode }) {
  return <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${colorClass}`}>{children}</span>;
}

/** Its parts in a row with "·" between them; a missing part (`null`) leaves no double "·". */
function DottedLine({ parts }: { parts: ReactNode[] }) {
  const presentParts = parts.filter((part) => part !== null);
  return (
    <div className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
      {presentParts.map((part, index) => (
        <Fragment key={index}>
          {index > 0 && <span className="text-slate-600">·</span>}
          {part}
        </Fragment>
      ))}
    </div>
  );
}

function DetailsSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">{title}</div>
      {children}
    </div>
  );
}

function EmptyNote({ children }: { children: ReactNode }) {
  return <p className="text-slate-500">{children}</p>;
}

// ── analysts ────────────────────────────────────────────────────────────────────────────────────

/** "target 340.02 (+2.04%)": the chosen source's target against IB's price; `null` without a target. */
function targetText(consensus: AnalystConsensus): string | null {
  if (consensus.averageTarget === null) return null;
  const upside =
    consensus.targetUpsidePercent === null ? '' : ` (${formatSignedPercent(consensus.targetUpsidePercent)})`;
  return `target ${formatMoney(consensus.averageTarget)}${upside}`;
}

/** The date of the chosen source's search result, as Claude copied it among every source's. */
function publishedDateOf(sourceUrl: string, sources: SourceConsensus[]): string | null {
  return sources.find((source) => source.sourceUrl === sourceUrl)?.publishedDate ?? null;
}

/**
 * "BUY · 42 analysts · target 340.02 (+2.04%) · marketbeat.com · copy of 2026-10-01": the consensus the API chose, its
 * source and how old the search engine's copy of it is.
 */
function ConsensusLine({ consensus, sources }: { consensus: AnalystConsensus; sources: SourceConsensus[] }) {
  const target = targetText(consensus);
  return (
    <DottedLine
      parts={[
        <ColoredLabel colorClass={labelClassOf(consensus.rating, RATING_TONES)}>
          {consensus.rating === null ? 'NO RATING' : labelTextOf(consensus.rating)}
        </ColoredLabel>,
        consensus.analystCount === null ? null : <span>{consensus.analystCount} analysts</span>,
        target === null ? null : <span className="font-mono">{target}</span>,
        <SourceLink url={consensus.sourceUrl}>{siteOf(consensus.sourceUrl)}</SourceLink>,
        <CopyDate date={publishedDateOf(consensus.sourceUrl, sources)} />,
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
    <p
      title={sources.map(sourceSummaryOf).join('\n')}
      className="cursor-help text-slate-400 underline decoration-slate-600 decoration-dotted underline-offset-2"
    >
      Targets{' '}
      <span className="font-mono">
        {formatMoney(consensus.targetLow)}–{formatMoney(consensus.targetHigh)}
      </span>{' '}
      across {consensus.sourceCount} sources
    </p>
  );
}

/**
 * "Trend IMPROVING · 3 targets raised, 0 lowered": the trend the API worked out from the price targets of the actions
 * listed under it. Without one, says why — no action at all, or ratings only.
 */
function TrendLine({ analysis }: { analysis: StockAnalysis }) {
  return (
    <DottedLine
      parts={[
        <span className="inline-flex items-center gap-1.5">
          Trend
          <ColoredLabel colorClass={labelClassOf(analysis.analystTrend, TREND_TONES)}>
            {analysis.analystTrend ?? 'UNKNOWN'}
          </ColoredLabel>
        </span>,
        analysis.analystTrend === null ? (
          <span className="text-slate-500">{noTrendReason(analysis)}</span>
        ) : (
          <span title="Only price targets count, among the actions below: a rating without a target moves nothing">
            {targetMovesText(analysis)}
          </span>
        ),
      ]}
    />
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

/** "2026-10-06 · Goldman Sachs · TARGET LOWERED · Hold · target 20.00 → 15.00 · moomoo.com · copy of 2026-10-07" */
function ActionRow({ action }: { action: AnalystAction }) {
  const ratingChange = ratingChangeOf(action);
  const targetChange = targetChangeOf(action);
  return (
    <li>
      <DottedLine
        parts={[
          <span className="font-mono text-slate-500">{action.date}</span>,
          <span className="text-slate-200">{action.firm}</span>,
          <ColoredLabel colorClass={labelClassOf(action.action, ACTION_TONES)}>{ACTION_NAMES[action.action]}</ColoredLabel>,
          ratingChange === null ? null : <span>{ratingChange}</span>,
          targetChange === null ? null : <span className="font-mono">{targetChange}</span>,
          <SourceLink url={action.url}>{siteOf(action.url)}</SourceLink>,
          <CopyDate date={action.sourcePublishedDate} />,
        ]}
      />
    </li>
  );
}

function AnalystsSection({ analysis }: { analysis: StockAnalysis }) {
  return (
    <DetailsSection title="Analysts">
      {analysis.consensus === null ? (
        <EmptyNote>No analyst consensus in the search results.</EmptyNote>
      ) : (
        <>
          <ConsensusLine consensus={analysis.consensus} sources={analysis.consensusBySource} />
          <TargetSpread consensus={analysis.consensus} sources={analysis.consensusBySource} />
        </>
      )}
      <TrendLine analysis={analysis} />
      {analysis.recentActions.length > 0 && (
        <ul className="flex flex-col gap-1">
          {analysis.recentActions.map((action, index) => (
            <ActionRow key={`${action.date}-${action.firm}-${index}`} action={action} />
          ))}
        </ul>
      )}
    </DetailsSection>
  );
}

// ── news ────────────────────────────────────────────────────────────────────────────────────────

/** "2026-10-08 · Zacks.com · Apple Rises 24% YTD…", the headline linking to the article. */
function HeadlineRow({ headline }: { headline: Headline }) {
  return (
    <li>
      <DottedLine
        parts={[
          headline.date === null ? null : <span className="font-mono text-slate-500">{headline.date}</span>,
          <span className="text-slate-400">{headline.source}</span>,
          <SourceLink url={headline.url}>{headline.title}</SourceLink>,
        ]}
      />
    </li>
  );
}

function NewsSection({ analysis }: { analysis: StockAnalysis }) {
  return (
    <DetailsSection title="News">
      <div className="flex flex-wrap items-center gap-1.5">
        <ColoredLabel colorClass={labelClassOf(analysis.sentiment, SENTIMENT_TONES)}>
          {analysis.sentiment ?? 'UNKNOWN'}
        </ColoredLabel>
        <span className="text-slate-300">{analysis.sentimentReason ?? 'No recent news in the search results.'}</span>
      </div>
      {analysis.headlines.length > 0 && (
        <ul className="flex flex-col gap-1">
          {analysis.headlines.map((headline, index) => (
            <HeadlineRow key={`${headline.url}-${index}`} headline={headline} />
          ))}
        </ul>
      )}
    </DetailsSection>
  );
}

// ── the box ─────────────────────────────────────────────────────────────────────────────────────

function notAnalyzedText(holding: Holding): string {
  if (holding.status === 'CLOSED') return 'Not analyzed: the analysis runs on open holdings only.';
  return 'Not analyzed yet. Analyze searches the web for its analyst ratings and news, and Claude reads what it finds.';
}

/**
 * In a holding's expanded row, under the momentum: its latest analysis — the analysts' consensus the API chose, with
 * the spread of the sources, their trend and latest actions, and the news — every fact linked to its source, and a
 * button to analyze this stock again (open holdings only: the API refuses a closed one).
 */
export function AnalysisDetails({ holding, onDataChanged }: { holding: Holding; onDataChanged: () => Promise<void> }) {
  const analysis = holding.analysis;
  return (
    <div className="flex flex-col gap-3 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
          Analysts and news
          {analysis !== null && (
            <>
              <SignalDot holding={holding} />
              <span className="font-normal normal-case text-slate-500">
                · {analyzedAtText(analysis)} by {analysis.model}
              </span>
            </>
          )}
        </div>
        {holding.status === 'OPEN' && (
          <AnalyzeButton
            holdingIds={[holding.id]}
            label={`Analyze ${holding.symbol}`}
            hint={`Search the web for the analyst ratings and news of ${holding.symbol}, and have Claude read them: about $0.03 and 3 Tavily credits`}
            onDataChanged={onDataChanged}
          />
        )}
      </div>
      {analysis === null ? (
        <EmptyNote>{notAnalyzedText(holding)}</EmptyNote>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            <AnalystsSection analysis={analysis} />
            <NewsSection analysis={analysis} />
          </div>
          <p className="text-[11px] text-slate-500">{SIGNAL_MEANINGS}</p>
        </>
      )}
    </div>
  );
}
