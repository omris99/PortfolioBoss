import { Check, CircleDashed, X } from 'lucide-react';
import { EMPTY_VALUE, formatMoney, formatSignedPercent } from '../lib/format';
import { labelClassOf, MOMENTUM_TONES } from '../lib/signalColors';
import { CHIP_CLASS } from '../lib/styles';
import type { Momentum, MomentumLabel } from '../types/portfolio';
import { HelpPopover } from './HelpPopover';

/** "4/5", "?/5" while one of the checks is unknown, "—" without a single stored close. */
export function momentumScoreText(momentum: Momentum | null): string {
  if (momentum === null) return EMPTY_VALUE;
  return `${momentum.score ?? '?'}/5`;
}

/** "STRONG 4/5", or the score alone while there is no label. */
export function momentumText(momentum: Momentum | null): string {
  if (momentum === null) return EMPTY_VALUE;
  return momentum.label === null ? momentumScoreText(momentum) : `${momentum.label} ${momentumScoreText(momentum)}`;
}

/** The label and score in the label's tone: "STRONG 5/5". */
export function MomentumChip({ momentum }: { momentum: Momentum | null }) {
  return (
    <span className={`${CHIP_CLASS} ${labelClassOf(momentum?.label ?? null, MOMENTUM_TONES)}`}>
      {momentum === null ? EMPTY_VALUE : `${momentum.label ?? 'UNKNOWN'} ${momentumScoreText(momentum)}`}
    </span>
  );
}

/** What each label suggests — a guide for reading the score, not advice; PortfolioBoss places no orders. */
const LABEL_MEANINGS: { label: MomentumLabel; range: string; meaning: string }[] = [
  { label: 'STRONG', range: '4–5', meaning: 'hold or add' },
  { label: 'NEUTRAL', range: '2–3', meaning: 'wait, don’t add' },
  { label: 'WEAK', range: '0–1', meaning: 'consider selling part' },
];

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

/** How many of the five checks hold: "3 of 5 checks pass" in the drawer's summary. */
export function passedCheckCount(momentum: Momentum): number {
  return checksOf(momentum).filter((check) => check.passed === true).length;
}

function CheckIcon({ passed }: { passed: boolean | null }) {
  if (passed === null) return <CircleDashed size={16} strokeWidth={2.5} className="text-unknown" aria-label="Unknown" />;
  return passed ? (
    <Check size={16} strokeWidth={2.5} className="text-positive" aria-label="Passes" />
  ) : (
    <X size={16} strokeWidth={2.5} className="text-negative" aria-label="Fails" />
  );
}

function textOf(check: MomentumCheck): string {
  if (check.passed === null) return check.unknownText;
  return check.passed ? check.passedText : check.failedText;
}

function CheckRow({ check }: { check: MomentumCheck }) {
  return (
    <li className="grid grid-cols-[20px_1fr_auto] items-center gap-3 px-4 py-2.5">
      <CheckIcon passed={check.passed} />
      <span>{textOf(check)}</span>
      <span className={`text-[13px] ${check.passed === null ? 'text-unknown' : 'text-muted'}`}>{check.detail}</span>
    </li>
  );
}

function LabelMeaningsHelp() {
  return (
    <HelpPopover label="What the momentum score means">
      <p>Five checks on the daily closes; the score counts the ones that pass.</p>
      <ul className="mt-2 grid gap-1.5">
        {LABEL_MEANINGS.map(({ label, range, meaning }) => (
          <li key={label} className="flex items-center gap-2">
            <span className={`${CHIP_CLASS} ${labelClassOf(label, MOMENTUM_TONES)}`}>
              {label} {range}
            </span>
            {meaning}
          </li>
        ))}
      </ul>
    </HelpPopover>
  );
}

/**
 * The drawer's Momentum tab (AI_ANALYSIS_TODO.md, decision 5): the label and score with the date of the closes, then
 * what each of the five checks found. Computed by the API from the daily closes the last run.sh read from IB.
 */
export function MomentumPanel({ momentum }: { momentum: Momentum | null }) {
  if (momentum === null) {
    return (
      <p className="py-6 text-muted">
        No daily closes stored yet: they are read from IB on every <span className="font-mono text-code">run.sh</span>.
      </p>
    );
  }
  return (
    <div>
      <div className="mb-4 flex min-h-7 items-center gap-2">
        <MomentumChip momentum={momentum} />
        <span className="text-muted">daily closes as of {momentum.asOf}</span>
        <span className="ml-auto">
          <LabelMeaningsHelp />
        </span>
      </div>
      <ul className="divide-y rounded-lg border">
        {checksOf(momentum).map((check) => (
          <CheckRow key={check.key} check={check} />
        ))}
      </ul>
    </div>
  );
}
