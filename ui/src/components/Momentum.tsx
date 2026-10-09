import { Check, CircleHelp, X } from 'lucide-react';
import { EMPTY_VALUE, formatMoney, formatSignedPercent } from '../lib/format';
import type { Momentum, MomentumLabel } from '../types/portfolio';

const LABEL_COLOR_CLASSES: Record<MomentumLabel, string> = {
  STRONG: 'bg-emerald-500/10 text-emerald-400',
  NEUTRAL: 'bg-amber-500/10 text-amber-400',
  WEAK: 'bg-rose-500/10 text-rose-400',
};
const UNKNOWN_COLOR_CLASS = 'bg-slate-800 text-slate-400';

/** What each label suggests — a legend for reading the score, not advice; PortfolioBoss places no orders. */
const LABEL_MEANINGS = 'STRONG 4–5: hold or add · NEUTRAL 2–3: wait, don’t add · WEAK 0–1: consider selling part';

function labelColorClass(label: MomentumLabel | null): string {
  return label === null ? UNKNOWN_COLOR_CLASS : LABEL_COLOR_CLASSES[label];
}

/** "4/5", or "?/5" while one of the checks is unknown. */
function scoreText(momentum: Momentum): string {
  return `${momentum.score ?? '?'}/5`;
}

function scoreHint(momentum: Momentum): string {
  const closesDate = `closes as of ${momentum.asOf}`;
  if (momentum.label === null) return `Momentum: no score yet, not enough daily closes for every check · ${closesDate}`;
  return `Momentum ${momentum.label} (${scoreText(momentum)}) · ${closesDate}`;
}

/** The Signal cell: "M 4/5" in the label's colour, the details on hover. "—" without a single stored close. */
export function MomentumScore({ momentum }: { momentum: Momentum | null }) {
  if (momentum === null) {
    return <span title="No daily closes stored yet: they are read from IB on every run.sh">{EMPTY_VALUE}</span>;
  }
  return (
    <span
      title={scoreHint(momentum)}
      className={`rounded px-1.5 py-0.5 font-sans text-[10px] font-semibold ${labelColorClass(momentum.label)}`}
    >
      M {scoreText(momentum)}
    </span>
  );
}

// ── the five checks ─────────────────────────────────────────────────────────────────────────────

interface MomentumCheck {
  key: string;
  passed: boolean | null;
  passedText: string;
  failedText: string;
  /** Why it is unknown, when it is. */
  unknownText: string;
  /** The figures it was decided on, e.g. "333.63 vs 333.41". */
  detail: string;
}

function figuresAgainst(first: number | null, second: number | null): string {
  return `${formatMoney(first)} vs ${formatMoney(second)}`;
}

function distanceFromHighText(momentum: Momentum): string {
  if (momentum.percentBelowHigh === null) return EMPTY_VALUE;
  if (momentum.percentBelowHigh <= 0) return `at the high, ${formatMoney(momentum.high20)}`;
  return `${momentum.percentBelowHigh.toFixed(1)}% below ${formatMoney(momentum.high20)}`;
}

function checksOf(momentum: Momentum): MomentumCheck[] {
  const notEnoughCloses = 'Not enough daily closes yet';
  return [
    {
      key: 'sma20',
      passed: momentum.aboveSma20,
      passedText: 'Above the 20-day average',
      failedText: 'Below the 20-day average',
      unknownText: notEnoughCloses,
      detail: figuresAgainst(momentum.lastClose, momentum.sma20),
    },
    {
      key: 'sma50',
      passed: momentum.aboveSma50,
      passedText: 'Above the 50-day average',
      failedText: 'Below the 50-day average',
      unknownText: notEnoughCloses,
      detail: figuresAgainst(momentum.lastClose, momentum.sma50),
    },
    {
      key: 'sma50-sma200',
      passed: momentum.sma50AboveSma200,
      passedText: '50-day average above the 200-day',
      failedText: '50-day average below the 200-day',
      unknownText: 'Fewer than 200 daily closes',
      detail: figuresAgainst(momentum.sma50, momentum.sma200),
    },
    {
      key: 'high',
      passed: momentum.nearHigh,
      passedText: 'Within 10% of the 20-day high',
      failedText: 'More than 10% below the 20-day high',
      unknownText: notEnoughCloses,
      detail: distanceFromHighText(momentum),
    },
    {
      key: 'spy',
      passed: momentum.beatsSpy,
      passedText: 'Ahead of SPY over the last month',
      failedText: 'Behind SPY over the last month',
      unknownText: 'No month of closes for it or for SPY',
      detail: `${formatSignedPercent(momentum.oneMonthReturnPercent)} vs SPY ${formatSignedPercent(
        momentum.spyOneMonthReturnPercent,
      )}`,
    },
  ];
}

function CheckIcon({ passed }: { passed: boolean | null }) {
  if (passed === null) return <CircleHelp size={12} className="shrink-0 text-slate-500" aria-label="unknown" />;
  return passed ? (
    <Check size={12} className="shrink-0 text-emerald-400" aria-label="holds" />
  ) : (
    <X size={12} className="shrink-0 text-rose-400" aria-label="fails" />
  );
}

function textOf(check: MomentumCheck): string {
  if (check.passed === null) return check.unknownText;
  return check.passed ? check.passedText : check.failedText;
}

function CheckRow({ check }: { check: MomentumCheck }) {
  return (
    <li className="flex items-center gap-1.5">
      <CheckIcon passed={check.passed} />
      <span className="text-slate-300">{textOf(check)}</span>
      <span className="font-mono text-slate-500">· {check.detail}</span>
    </li>
  );
}

/**
 * At the top of a holding's expanded row: the score, what each of the five checks found, and what the labels mean
 * (AI_ANALYSIS_TODO.md, decision 5). Computed by the API from the daily closes the last run.sh read from IB.
 */
export function MomentumDetails({ momentum }: { momentum: Momentum | null }) {
  return (
    <div className="flex flex-col gap-2 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs">
      <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-wide text-slate-400">
        Momentum
        {momentum !== null && (
          <>
            <span className={`rounded px-1.5 py-0.5 text-[10px] ${labelColorClass(momentum.label)}`}>
              {momentum.label ?? 'no score'} {scoreText(momentum)}
            </span>
            <span className="font-normal normal-case text-slate-500">· daily closes as of {momentum.asOf}</span>
          </>
        )}
      </div>
      {momentum === null ? (
        <p className="text-slate-500">No daily closes stored yet: they are read from IB on every run.sh.</p>
      ) : (
        <>
          <ul className="flex flex-col gap-1">
            {checksOf(momentum).map((check) => (
              <CheckRow key={check.key} check={check} />
            ))}
          </ul>
          <p className="text-[11px] text-slate-500">{LABEL_MEANINGS}</p>
        </>
      )}
    </div>
  );
}
