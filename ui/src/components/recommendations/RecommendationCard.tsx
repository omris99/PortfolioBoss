import type { ReactNode } from 'react';
import { Info, ScanSearch } from 'lucide-react';
import type { RecommendationAnalysisState } from '../../hooks/useRecommendationAnalysis';
import { formatDateTime, EMPTY_VALUE } from '../../lib/format';
import { CARD_CLASS } from '../../lib/styles';
import type { EstimatedAnalysisCost } from '../../types/portfolio';
import type { HotStock, LatestRecommendation, PickedStock, Recommendations } from '../../types/recommendations';
import { costEstimateOf } from '../AnalyzeControls';
import { TargetUpside } from './AnalystTarget';
import { AnalyzeShortListControl, shortListCountsOf, shortListWords, type ShortListCounts } from './AnalyzeButtons';
import { formatPoints, NEWS_SCORE_HINT, NewsScoreParts, pointsColorClass } from './NewsScore';
import { NotPickableReasons } from './NotPickableReasons';
import { RiskSigns } from './RiskSignTag';
import { earningsText, TimingNote } from './TimingNote';
import { sectorText } from './hotStockTexts';

/**
 * The recommendation (DESIGN_BRIEF.md §8.2; RECOMMENDATIONS_TODO.md, decisions 5, 12, 13 and 14): up to 3 stocks the
 * code picked from the short list, in its order, each with what the figures say about it and the news that raised it —
 * no text from Claude, who only reads the news. The latest recommendation stays even after a newer scan, with the date
 * of its own. Recommended means picked by rules in code, not a price forecast; nothing is bought.
 */

const FOOT_TEXT =
  'Picked by rules in code — the analysts’ target first, then the risk signs, then the news score — not a price ' +
  'forecast. Claude only reads the news. Nothing is bought.';

function sameInstant(firstIsoInstant: string, secondIsoInstant: string): boolean {
  return Date.parse(firstIsoInstant) === Date.parse(secondIsoInstant);
}

/** The pick's row in today's lists, by `conId`: a pick of an earlier scan is a new row today — or in no list any more. */
function stockOfPick(pick: PickedStock, stocks: HotStock[]): HotStock | null {
  return stocks.find((stock) => stock.conId === pick.conId) ?? null;
}

/** The symbol as a button that opens the stock's drawer. */
function SymbolButton({
  stock,
  onOpen,
  className = '',
}: {
  stock: HotStock;
  onOpen: (candidateId: number) => void;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(stock.candidateId)}
      className={`rounded-sm font-semibold underline-offset-2 hover:text-accent hover:underline ${className}`}
    >
      {stock.symbol}
    </button>
  );
}

// ── one pick ────────────────────────────────────────────────────────────────────────────────────

/**
 * "Pulled back 7.4% from its high · the analysts' target 27.1% above the price, of 36 firms · earnings on 2026-11-12,
 * after the close": what the figures add to the signs, written by the code. The target is Dow Jones's, the rule's.
 */
function pickFactsText(stock: HotStock): string {
  const facts: string[] = [];
  if (stock.pulledBack) facts.push(`pulled back ${stock.momentum.percentBelowHigh52Weeks.toFixed(1)}% from its high`);
  const targetUpside = stock.analystTarget.upsidePercent;
  if (targetUpside !== null) {
    const side = targetUpside >= 0 ? 'above' : 'below';
    facts.push(
      `the analysts’ target ${Math.abs(targetUpside).toFixed(1)}% ${side} the price, of ` +
        `${stock.analystTarget.firmCount} firms`,
    );
  }
  if (stock.nextEarningsDate !== null) facts.push(`earnings on ${earningsText(stock.nextEarningsDate)}`);
  if (facts.length === 0) return 'Nothing more in the figures.';
  const sentence = facts.join(' · ');
  return sentence.charAt(0).toUpperCase() + sentence.slice(1);
}

/** "Score +4 · news +5 · risk signs weigh 1": what the picks are ordered by, and its two parts. */
function ScoreLine({ stock }: { stock: HotStock }) {
  if (stock.score === null || stock.newsScore === null) return null;
  return (
    <p className="text-caption text-muted">
      <span title="The news score less the risk signs’ weight: the picks are ordered by it">
        Score <b className={`font-semibold ${pointsColorClass(stock.score)}`}>{formatPoints(stock.score)}</b>
      </span>
      {' · '}
      <span title={NEWS_SCORE_HINT}>news {formatPoints(stock.newsScore)}</span>
      {' · '}risk signs weigh {stock.riskWeight}
    </p>
  );
}

function PickNumber({ position }: { position: number }) {
  return (
    <span
      aria-label={`Pick ${position}`}
      className="inline-flex h-[22px] w-[22px] shrink-0 items-center justify-center rounded-full bg-ink text-caption font-semibold text-surface"
    >
      {position}
    </span>
  );
}

/** A pick no longer in today's lists — after a newer scan it may have left them; only its symbol is known. */
function PickNotInTheLists({ position, pick }: { position: number; pick: PickedStock }) {
  return (
    <div className="rounded-lg border p-4">
      <div className="flex items-center gap-2">
        <PickNumber position={position} />
        <span className="text-[16px] font-semibold leading-[22px]">{pick.symbol}</span>
      </div>
      <p className="mt-2 text-muted">Not in today’s lists: the newer scan left it out.</p>
    </div>
  );
}

/**
 * One pick, a third of the card so 1, 2 or 3 keep the same size: its number, symbol, name and sector; its risk signs
 * and the timing note apart; the code's line from the figures; the score; and the news that raised it.
 */
function PickCard({
  position,
  stock,
  onOpen,
}: {
  position: number;
  stock: HotStock;
  onOpen: (candidateId: number) => void;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-2.5 rounded-lg border p-4">
      <div className="flex min-w-0 items-center gap-2">
        <PickNumber position={position} />
        <SymbolButton stock={stock} onOpen={onOpen} className="text-[16px] leading-[22px]" />
        <span title={stock.companyName ?? undefined} className="min-w-0 truncate text-[11px] uppercase text-muted">
          {stock.companyName ?? EMPTY_VALUE}
        </span>
      </div>
      <p className="-mt-1.5 text-caption text-muted">{sectorText(stock)}</p>
      <div className="flex flex-wrap items-center gap-1.5 text-caption">
        <RiskSigns stock={stock} showFigures />
        {stock.reportsWithin14Days && stock.nextEarningsDate !== null && (
          <TimingNote earningsDate={stock.nextEarningsDate} showsDateOnly={false} />
        )}
      </div>
      <p className="rounded-md bg-subtle px-3 py-2 text-[13px] leading-[19px]">{pickFactsText(stock)}</p>
      <ScoreLine stock={stock} />
      <div className="text-[13px] leading-[19px]">
        <NewsScoreParts stock={stock} />
      </div>
    </div>
  );
}

// ── the stocks not picked ───────────────────────────────────────────────────────────────────────

/** A stock analyzed on its own after the recommendation: its reason is from that newer analysis, not the one weighed. */
function wasAnalyzedAgainSince(stock: HotStock, recommendation: LatestRecommendation): boolean {
  return stock.analysis !== null && Date.parse(stock.analysis.analyzedAt) > Date.parse(recommendation.recommendedAt);
}

/** Why the code passed over a stock it analyzed, by the rule — from its analysis, as the page reads it today. */
function NotPickedReason({ stock }: { stock: HotStock }) {
  if (stock.analysis === null) return <span className="text-muted">not analyzed: its analysis failed</span>;
  if (!stock.canBePicked) return <NotPickableReasons stock={stock} />;
  return (
    <>
      {stock.riskSigns !== null && stock.riskSigns.length > 0 && <RiskSigns stock={stock} showFigures />}
      <span className="text-muted">
        score {stock.score === null ? EMPTY_VALUE : formatPoints(stock.score)}, below the picks
      </span>
    </>
  );
}

/** One reason for many stocks, in a row: "Analysts' target under +15%: SNAP +4.2% …", each symbol opening its drawer. */
function StocksPassedOver({
  reason,
  stocks,
  showsUpside,
  onOpen,
}: {
  reason: string;
  stocks: HotStock[];
  showsUpside: boolean;
  onOpen: (candidateId: number) => void;
}) {
  if (stocks.length === 0) return null;
  return (
    <li className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1 text-caption">
      <span className="text-muted">{reason}:</span>
      {stocks.map((stock) => (
        <span key={stock.candidateId} className="inline-flex items-baseline gap-1">
          <SymbolButton stock={stock} onOpen={onOpen} className="text-[13px]" />
          {showsUpside && <TargetUpside analystTarget={stock.analystTarget} />}
        </span>
      ))}
    </li>
  );
}

/**
 * "Why not the others": every hot stock the code didn't pick, with its reason — no text from Claude. The ones it
 * analyzed one by one; the rest by their analysts' target, which kept them off the short list (decision 14). Today's
 * lists are the recommendation's only while it is of the latest scan.
 */
function WhyNotTheOthers({
  recommendation,
  hotStocks,
  onOpen,
}: {
  recommendation: LatestRecommendation;
  hotStocks: HotStock[];
  onOpen: (candidateId: number) => void;
}) {
  const pickedConIds = new Set(recommendation.picks.map((pick) => pick.conId));
  const notPicked = hotStocks.filter((stock) => !pickedConIds.has(stock.conId));
  if (notPicked.length === 0) return null;
  const analyzed = notPicked.filter((stock) => stock.inShortList);
  const offTheShortList = notPicked.filter((stock) => !stock.inShortList);
  const pastTheFirstTen = offTheShortList.filter((stock) => stock.analystTarget.hasEnoughUpside);
  const targetTooClose = offTheShortList.filter((stock) => stock.reasonsNotPickable.includes('TARGET_TOO_CLOSE'));
  const withoutATarget = offTheShortList.filter((stock) => stock.reasonsNotPickable.includes('NO_ANALYST_TARGET'));
  return (
    <div className="border-t px-5 py-3">
      <h3 className="mb-1.5 text-caption font-semibold text-muted">Why not the others</h3>
      <ul className="flex flex-col gap-1">
        {analyzed.map((stock) => (
          <li key={stock.candidateId} className="flex flex-wrap items-center gap-x-2 gap-y-1 text-caption">
            <SymbolButton stock={stock} onOpen={onOpen} className="w-14 text-left text-[13px]" />
            <NotPickedReason stock={stock} />
            {wasAnalyzedAgainSince(stock, recommendation) && <span className="text-faint">· analyzed again since</span>}
          </li>
        ))}
        <StocksPassedOver
          reason="Past the first 10 with the analysts’ target 15%+ above the price"
          stocks={pastTheFirstTen}
          showsUpside
          onOpen={onOpen}
        />
        <StocksPassedOver reason="Analysts’ target under +15%" stocks={targetTooClose} showsUpside onOpen={onOpen} />
        <StocksPassedOver
          reason="No analysts’ target, fewer than 3 firms"
          stocks={withoutATarget}
          showsUpside={false}
          onOpen={onOpen}
        />
      </ul>
    </div>
  );
}

// ── the card ────────────────────────────────────────────────────────────────────────────────────

function CardFoot() {
  return (
    <p className="flex items-center gap-1.5 border-t px-5 py-2.5 text-caption text-faint">
      <Info size={14} className="shrink-0" aria-hidden="true" />
      {FOOT_TEXT}
    </p>
  );
}

/** Before any recommendation: a calm invitation, with the button and what it should cost. */
function NoRecommendationYet({
  shortList,
  estimate,
  analyze,
}: {
  shortList: ShortListCounts;
  estimate: EstimatedAnalysisCost | null;
  analyze: ReactNode;
}) {
  const cost = costEstimateOf(estimate, shortList.analyzed);
  return (
    <section aria-label="Recommendation" className={CARD_CLASS}>
      <div className="flex items-center gap-4 px-5 py-4">
        <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-accent-soft text-accent">
          <ScanSearch size={20} aria-hidden="true" />
        </span>
        <div className="min-w-0">
          <h2 className="text-section-title">No recommendation yet</h2>
          <p className="mt-0.5 text-[13px] text-muted">
            {shortList.analyzed === 0
              ? 'No hot stock has the analysts’ target 15% or more above its price: nothing to analyze.'
              : `Analyze & recommend reads the analyst ratings, the news and the company figures of ` +
                `${shortListWords(shortList)}; then the code picks up to 3 by their risk signs and news score.`}
          </p>
        </div>
        <div className="ml-auto flex shrink-0 flex-col items-end gap-1">
          {analyze}
          {shortList.analyzed > 0 && (
            <span className="text-caption text-faint">
              {cost === null
                ? 'What it costs shows once a stock has been analyzed'
                : `About ${cost.credits} Tavily credits and $${cost.dollars}`}
            </span>
          )}
        </div>
      </div>
    </section>
  );
}

/** "Recommended 2026-10-24 09:12, from the scan of 2026-10-23 23:05" — and, when a newer scan came since, that it did. */
function RecommendationHead({
  recommendation,
  isFromLatestScan,
  analyze,
}: {
  recommendation: LatestRecommendation;
  isFromLatestScan: boolean;
  analyze: ReactNode;
}) {
  return (
    <div className="flex items-center gap-4 border-b px-5 py-3">
      <div className="min-w-0">
        <h2 className="text-section-title">Recommendation</h2>
        <p className="text-caption text-muted">
          Recommended {formatDateTime(recommendation.recommendedAt)}, from the scan of{' '}
          {formatDateTime(recommendation.scannedAt)}
          {!isFromLatestScan && ' · a newer scan came since: Analyze & recommend picks from today’s lists'}
        </p>
      </div>
      <div className="ml-auto flex min-w-0 items-center">{analyze}</div>
    </div>
  );
}

function NoStockQualified() {
  return (
    <p className="mx-5 my-4 flex items-start gap-2 rounded-md bg-subtle px-3 py-2.5">
      <Info size={16} className="mt-0.5 shrink-0 text-muted" aria-hidden="true" />
      No stock qualified this time: none had the analysts’ target 15% or more above its price, or every one analyzed
      had risk signs that weigh more than 2, or no figures from Finnhub.
    </p>
  );
}

/**
 * The card between the scan card and the lists, in three states: none yet (the invitation), the picks, or no stock
 * qualified. `onOpen` opens a stock's drawer from its symbol.
 */
export function RecommendationCard({
  recommendations,
  estimate,
  analysisState,
  onAnalyze,
  onOpen,
}: {
  recommendations: Recommendations;
  estimate: EstimatedAnalysisCost | null;
  analysisState: RecommendationAnalysisState;
  onAnalyze: (candidateId: number | null) => void;
  onOpen: (candidateId: number) => void;
}) {
  const recommendation = recommendations.latestRecommendation;
  const shortList = shortListCountsOf(recommendations.hotStocks);
  const analyzeControl = (isPrimary: boolean) => (
    <AnalyzeShortListControl
      shortList={shortList}
      estimate={estimate}
      analysisState={analysisState}
      onAnalyze={onAnalyze}
      isPrimary={isPrimary}
    />
  );

  if (recommendation === null) {
    return <NoRecommendationYet shortList={shortList} estimate={estimate} analyze={analyzeControl(true)} />;
  }

  const { hotStocks, tooHotStocks, correctingStocks } = recommendations;
  const stocksInLists = [...hotStocks, ...tooHotStocks, ...correctingStocks];
  const isFromLatestScan = sameInstant(recommendation.scannedAt, recommendations.scannedAt);
  return (
    <section aria-label="Recommendation" className={CARD_CLASS}>
      <RecommendationHead
        recommendation={recommendation}
        isFromLatestScan={isFromLatestScan}
        analyze={analyzeControl(false)}
      />
      {recommendation.picks.length === 0 ? (
        <NoStockQualified />
      ) : (
        <div className="grid grid-cols-3 gap-4 px-5 py-4">
          {recommendation.picks.map((pick, index) => {
            const stock = stockOfPick(pick, stocksInLists);
            return stock === null ? (
              <PickNotInTheLists key={pick.candidateId} position={index + 1} pick={pick} />
            ) : (
              <PickCard key={pick.candidateId} position={index + 1} stock={stock} onOpen={onOpen} />
            );
          })}
        </div>
      )}
      {isFromLatestScan && (
        <WhyNotTheOthers recommendation={recommendation} hotStocks={recommendations.hotStocks} onOpen={onOpen} />
      )}
      <CardFoot />
    </section>
  );
}
