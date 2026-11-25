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
 * means the same thing everywhere: green good, amber in between, red a warning sign, grey unknown or neither way.
 */
type SignalTone = 'good' | 'inBetween' | 'warning' | 'plain';

/** The tones as text: a verdict's word, a momentum score. No tinted background since round 6 — verdicts are words. */
const TEXT_CLASSES: Record<SignalTone, string> = {
  good: 'text-positive',
  inBetween: 'text-inbetween',
  warning: 'text-negative',
  plain: 'text-unknown',
};

/** The same tones as the fill of a drawing: the momentum mark's bars. */
const FILL_CLASSES: Record<SignalTone, string> = {
  good: 'fill-positive',
  inBetween: 'fill-inbetween',
  warning: 'fill-negative',
  plain: 'fill-unknown',
};

/** The dot: filled in its colour, an empty ring while there is none. */
const DOT_CLASSES: Record<SignalTone, string> = {
  good: 'bg-signal-green',
  inBetween: 'bg-signal-yellow',
  warning: 'bg-signal-red',
  plain: 'ring-[1.5px] ring-inset ring-signal-none',
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

/** A verdict's color for a value, e.g. `labelClassOf(rating, RATING_TONES)`; grey when it is unknown. */
export function labelClassOf<Value extends string>(value: Value | null, tones: Record<Value, SignalTone>): string {
  return TEXT_CLASSES[value === null ? 'plain' : tones[value]];
}

/** The fill of a drawing for a value, e.g. `fillClassOf(momentum.label, MOMENTUM_TONES)`; grey when it is unknown. */
export function fillClassOf<Value extends string>(value: Value | null, tones: Record<Value, SignalTone>): string {
  return FILL_CLASSES[value === null ? 'plain' : tones[value]];
}

/** The text colour alone for a value, e.g. `textClassOf(momentum.label, MOMENTUM_TONES)`; grey when it is unknown. */
export function textClassOf<Value extends string>(value: Value | null, tones: Record<Value, SignalTone>): string {
  return TEXT_CLASSES[value === null ? 'plain' : tones[value]];
}

export function dotClassOf(signal: HoldingSignal | null): string {
  return DOT_CLASSES[signal === null ? 'plain' : SIGNAL_TONES[signal]];
}
