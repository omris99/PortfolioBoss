import type {
  AnalystActionType,
  AnalystRating,
  AnalystTrend,
  HoldingSignal,
  MomentumLabel,
  Sentiment,
} from '../types/portfolio';

/**
 * The colours every part of a holding's signal is shown in — the momentum, the analysts, the news, the dot — so a colour
 * means the same thing everywhere: emerald good, amber in between, rose a warning sign, grey unknown or neither way.
 */
type SignalTone = 'good' | 'inBetween' | 'warning' | 'plain';

/** A small label: a tinted background behind the text. */
const LABEL_CLASSES: Record<SignalTone, string> = {
  good: 'bg-emerald-500/10 text-emerald-400',
  inBetween: 'bg-amber-500/10 text-amber-400',
  warning: 'bg-rose-500/10 text-rose-400',
  plain: 'bg-slate-800 text-slate-400',
};

/** The dot: filled in its colour, an empty ring while there is none. */
const DOT_CLASSES: Record<SignalTone, string> = {
  good: 'bg-emerald-400',
  inBetween: 'bg-amber-400',
  warning: 'bg-rose-400',
  plain: 'border border-slate-600',
};

export const MOMENTUM_TONES: Record<MomentumLabel, SignalTone> = {
  STRONG: 'good',
  NEUTRAL: 'inBetween',
  WEAK: 'warning',
};

export const TREND_TONES: Record<AnalystTrend, SignalTone> = {
  IMPROVING: 'good',
  STABLE: 'inBetween',
  DETERIORATING: 'warning',
};

export const SENTIMENT_TONES: Record<Sentiment, SignalTone> = {
  POSITIVE: 'good',
  NEUTRAL: 'inBetween',
  NEGATIVE: 'warning',
};

export const RATING_TONES: Record<AnalystRating, SignalTone> = {
  STRONG_BUY: 'good',
  BUY: 'good',
  HOLD: 'inBetween',
  SELL: 'warning',
  STRONG_SELL: 'warning',
};

/** A raise or an upgrade is good news, a cut or a downgrade bad; starting or keeping coverage is neither. */
export const ACTION_TONES: Record<AnalystActionType, SignalTone> = {
  UPGRADE: 'good',
  TARGET_RAISED: 'good',
  DOWNGRADE: 'warning',
  TARGET_LOWERED: 'warning',
  INITIATE: 'plain',
  REITERATE: 'plain',
};

export const SIGNAL_TONES: Record<HoldingSignal, SignalTone> = {
  GREEN: 'good',
  YELLOW: 'inBetween',
  RED: 'warning',
};

/** The label classes for a value, e.g. `labelClassOf(momentum.label, MOMENTUM_TONES)`; grey when it is unknown. */
export function labelClassOf<Value extends string>(value: Value | null, tones: Record<Value, SignalTone>): string {
  return LABEL_CLASSES[value === null ? 'plain' : tones[value]];
}

export function dotClassOf(signal: HoldingSignal | null): string {
  return DOT_CLASSES[signal === null ? 'plain' : SIGNAL_TONES[signal]];
}
