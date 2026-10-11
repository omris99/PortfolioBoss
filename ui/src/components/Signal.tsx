import { formatDateTime } from '../lib/format';
import { dotClassOf, MOMENTUM_TONES, textClassOf } from '../lib/signalColors';
import type { Holding, HoldingSignal, StockAnalysis } from '../types/portfolio';
import { momentumScoreText, momentumText } from './Momentum';

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

/**
 * The warning signs the dot counts, in plain words: "Momentum weak", "Analysts down", "News negative" — only those
 * found. The API decides the dot; these say why it has its colour.
 */
export function warningSignsOf(holding: Holding): string[] {
  const warningSigns: string[] = [];
  if (holding.momentum?.label === 'WEAK') warningSigns.push('Momentum weak');
  if (holding.analysis?.analystTrend === 'DETERIORATING') warningSigns.push('Analysts down');
  if (holding.analysis?.sentiment === 'NEGATIVE') warningSigns.push('News negative');
  return warningSigns;
}

/** A dot of one colour, `label` said on hover and to screen readers; an empty ring for `null`. */
export function SignalColorDot({
  signal,
  label,
  size = 10,
}: {
  signal: HoldingSignal | null;
  label: string;
  size?: number;
}) {
  return (
    <span
      role="img"
      aria-label={label}
      title={label}
      style={{ width: size, height: size }}
      className={`inline-block shrink-0 rounded-full ${dotClassOf(signal)}`}
    />
  );
}

/** A holding's coloured dot, what it means on hover; an empty ring while there is none. */
export function SignalDot({ holding, size }: { holding: Holding; size?: number }) {
  return <SignalColorDot signal={holding.signal} label={signalHint(holding)} size={size} />;
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

/** How to read the Signal column, behind the "?" in its header. */
export function SignalCellLegend() {
  return (
    <>
      <p>
        The dot is the signal: <b className="font-semibold">GREEN</b> no warning sign,{' '}
        <b className="font-semibold">YELLOW</b> one, <b className="font-semibold">RED</b> two or more; an empty ring
        until the first analysis.
      </p>
      <p className="mt-2">
        Next to it: the warning signs it found (weak momentum, analysts down, negative news), then the momentum score
        out of 5. Hover for all three parts.
      </p>
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

/** "Signal GREEN: no warning sign · Momentum STRONG 5/5 · Analysts IMPROVING · News POSITIVE": all three parts. */
function signalSummaryText(holding: Holding): string {
  const analysis = holding.analysis;
  const signalPart =
    holding.signal === null ? 'No signal' : `Signal ${holding.signal}: ${SIGNAL_DESCRIPTIONS[holding.signal]}`;
  const parts = [analysis === null ? 'Not analyzed yet' : signalPart, `Momentum ${momentumText(holding.momentum)}`];
  if (analysis !== null) {
    parts.push(`Analysts ${analysis.analystTrend ?? `no trend (${noTrendReason(analysis)})`}`);
    parts.push(`News ${analysis.sentiment ?? 'unknown'}`);
  }
  return parts.join(' · ');
}

/** What the cell says after the dot: the warning signs found, or that there are none, or that it isn't analyzed. */
function WarningSignsText({ holding }: { holding: Holding }) {
  if (holding.analysis === null) return <span className="text-unknown">Not analyzed</span>;
  const warningSigns = warningSignsOf(holding);
  if (warningSigns.length === 0) return <span className="text-faint">No warning signs</span>;
  return <span className="text-negative">{warningSigns.join(' · ')}</span>;
}

/**
 * The Signal column: the dot, the warning signs it found in words ("Momentum weak · Analysts down"), and the momentum
 * score at the right in its tone. It says why the dot has its colour; hovering gives all three parts.
 */
export function SignalCell({ holding }: { holding: Holding }) {
  const scoreClass = textClassOf(holding.momentum?.label ?? null, MOMENTUM_TONES);
  return (
    <span title={signalSummaryText(holding)} className="flex w-full min-w-[200px] items-center gap-1.5">
      <SignalColorDot signal={holding.signal} label={signalHint(holding)} />
      <WarningSignsText holding={holding} />
      <span className={`ml-auto pl-2 text-caption font-semibold ${scoreClass}`}>
        {momentumScoreText(holding.momentum)}
      </span>
    </span>
  );
}
