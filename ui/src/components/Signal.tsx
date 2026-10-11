import { formatDateTime } from '../lib/format';
import { CHIP_CLASS } from '../lib/styles';
import { dotClassOf, labelClassOf, SENTIMENT_TONES, TREND_TONES } from '../lib/signalColors';
import type { Holding, HoldingSignal, StockAnalysis } from '../types/portfolio';
import { MomentumScore } from './Momentum';

/** What the dot's colours mean (AI_ANALYSIS_TODO.md, decision 6): the API decides the colour, the UI only explains it. */
export const SIGNAL_MEANINGS =
  'Dot: green, no warning sign · yellow, one · red, two or more — of weak momentum, analysts deteriorating, negative news';

const SIGNAL_DESCRIPTIONS: Record<HoldingSignal, string> = {
  GREEN: 'no warning sign',
  YELLOW: 'one warning sign',
  RED: 'two warning signs or more',
};

/** "analyzed 2026-10-09 11:41", in this computer's time zone. */
export function analyzedAtText(analysis: StockAnalysis): string {
  return `analyzed ${formatDateTime(analysis.analyzedAt)}`;
}

function signalHint(holding: Holding): string {
  if (holding.analysis === null) return 'No signal yet: press Analyze';
  const analyzedAt = analyzedAtText(holding.analysis);
  if (holding.signal === null) {
    return `No signal: fewer than two of the momentum, the analysts and the news are known · ${analyzedAt}`;
  }
  return `${holding.signal}: ${SIGNAL_DESCRIPTIONS[holding.signal]} · ${analyzedAt}`;
}

/** A dot of one colour, `label` said on hover and to screen readers; an empty ring for `null`. */
export function SignalColorDot({ signal, label }: { signal: HoldingSignal | null; label: string }) {
  return (
    <span
      role="img"
      aria-label={label}
      title={label}
      className={`inline-block h-2.5 w-2.5 shrink-0 rounded-full ${dotClassOf(signal)}`}
    />
  );
}

/** A holding's coloured dot, what it means on hover; an empty ring while there is none. */
export function SignalDot({ holding }: { holding: Holding }) {
  return <SignalColorDot signal={holding.signal} label={signalHint(holding)} />;
}

/** What the colours mean, behind a "?": the warning signs, then each colour with its dot. */
export function SignalLegend() {
  const legendRows: { signal: HoldingSignal | null; name: string; meaning: string }[] = [
    { signal: 'GREEN', name: 'GREEN', meaning: 'no warning sign' },
    { signal: 'YELLOW', name: 'YELLOW', meaning: 'one' },
    { signal: 'RED', name: 'RED', meaning: 'two or more' },
    { signal: null, name: 'Empty ring', meaning: 'no signal yet: not analyzed, or too little known' },
  ];
  return (
    <>
      <p>The dot counts warning signs: weak momentum, analysts deteriorating, negative news.</p>
      <ul className="mt-2 grid gap-1.5">
        {legendRows.map((row) => (
          <li key={row.name} className="flex items-center gap-2">
            <SignalColorDot signal={row.signal} label={row.name} />
            <span>
              <b className="font-semibold">{row.name}</b> · {row.meaning}
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}

/** "3 targets raised, 0 lowered": what the API weighed for the trend. */
export function targetMovesText(analysis: StockAnalysis): string {
  return `${analysis.raisedTargetCount} targets raised, ${analysis.loweredTargetCount} lowered`;
}

/** Why there is no trend: no action at all, or only ratings without a price target (Zacks, Weiss…). */
export function noTrendReason(analysis: StockAnalysis): string {
  return analysis.recentActions.length === 0
    ? 'no analyst actions in the search results'
    : 'no price targets among the analyst actions';
}

function analystsHint(analysis: StockAnalysis): string {
  if (analysis.analystTrend === null) return `Analysts: ${noTrendReason(analysis)}`;
  return `Analysts ${analysis.analystTrend}: ${targetMovesText(analysis)}`;
}

function newsHint(analysis: StockAnalysis): string {
  if (analysis.sentiment === null) return 'News: no recent news in the search results';
  const reason = analysis.sentimentReason === null ? '' : `: ${analysis.sentimentReason}`;
  return `News ${analysis.sentiment}${reason}`;
}

/** "A" or "N" in the colour of what was found, the details on hover. */
function LetterLabel({ letter, colorClass, hint }: { letter: string; colorClass: string; hint: string }) {
  return (
    <span title={hint} className={`${CHIP_CLASS} ${colorClass}`}>
      {letter}
    </span>
  );
}

/**
 * The Signal cell: the dot, then the three things it is made of, each in its own colour — "M 4/5" the momentum, "A" the
 * analysts' trend, "N" the news. Before the first analysis only the momentum.
 */
export function SignalCell({ holding }: { holding: Holding }) {
  const analysis = holding.analysis;
  return (
    <span className="inline-flex items-center gap-1.5">
      <SignalDot holding={holding} />
      <MomentumScore momentum={holding.momentum} />
      {analysis !== null && (
        <>
          <LetterLabel
            letter="A"
            colorClass={labelClassOf(analysis.analystTrend, TREND_TONES)}
            hint={analystsHint(analysis)}
          />
          <LetterLabel
            letter="N"
            colorClass={labelClassOf(analysis.sentiment, SENTIMENT_TONES)}
            hint={newsHint(analysis)}
          />
        </>
      )}
    </span>
  );
}
