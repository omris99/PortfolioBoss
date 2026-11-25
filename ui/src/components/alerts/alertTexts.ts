import type { AlertType } from '../../types/alerts';

/**
 * What a type's number counts, and the numbers the server allows for it — a hand copy of `alerts.ThresholdUnit`
 * (1–60 whole days, 1–100 percent): change both together. The drawer checks them before sending, so that the field turns
 * red in place of the server's refusal.
 */
export interface ThresholdRange {
  unit: string;
  smallest: number;
  largest: number;
  wholeNumbersOnly: boolean;
}

const DAYS: ThresholdRange = { unit: 'days', smallest: 1, largest: 60, wholeNumbersOnly: true };
const PERCENT: ThresholdRange = { unit: '%', smallest: 1, largest: 100, wholeNumbersOnly: false };

/**
 * A type's sentence in the settings drawer — and, for a type with a number, the words before its field and after it,
 * the unit among them (round 6: the field sits inside its sentence): "Reports within [7] days".
 */
export type AlertSettingLine =
  | { kind: 'plain'; text: string }
  | { kind: 'withNumber'; textBeforeNumber: string; range: ThresholdRange; textAfterNumber: string };

export const ALERT_SETTING_LINES: Record<AlertType, AlertSettingLine> = {
  MOMENTUM_TURNED_WEAK: { kind: 'plain', text: 'Momentum turned weak' },
  CLOSED_BELOW_SMA50: { kind: 'plain', text: 'Closed below its 50-day average' },
  CLOSED_BELOW_SMA200: { kind: 'plain', text: 'Closed below its 200-day average' },
  EARNINGS_SOON: { kind: 'withNumber', textBeforeNumber: 'Reports within', range: DAYS, textAfterNumber: 'days' },
  ANALYST_CUT_TARGET: {
    kind: 'withNumber',
    textBeforeNumber: 'An analyst cut the target in the last',
    range: DAYS,
    textAfterNumber: 'days',
  },
  FAR_BELOW_AVERAGE_COST: {
    kind: 'withNumber',
    textBeforeNumber: 'The price',
    range: PERCENT,
    textAfterNumber: '% or more below the average cost',
  },
  ABOVE_WEIGHT_LIMIT: {
    kind: 'withNumber',
    textBeforeNumber: 'More than',
    range: PERCENT,
    textAfterNumber: '% of the whole account',
  },
  TRADES_DONT_MATCH_IB: { kind: 'plain', text: 'The trades entered don’t match IB' },
  // Its group's heading says Recommendations already.
  ENTERED_SHORT_LIST: { kind: 'plain', text: 'A stock entered the short list' },
};

/** The drawer's sections, in the order of `alerts.AlertType`. */
export const ALERT_SETTING_GROUPS: { heading: string; types: AlertType[] }[] = [
  { heading: 'Momentum', types: ['MOMENTUM_TURNED_WEAK', 'CLOSED_BELOW_SMA50', 'CLOSED_BELOW_SMA200'] },
  { heading: 'Earnings', types: ['EARNINGS_SOON'] },
  { heading: 'Analysts', types: ['ANALYST_CUT_TARGET'] },
  { heading: 'My holdings', types: ['FAR_BELOW_AVERAGE_COST', 'ABOVE_WEIGHT_LIMIT', 'TRADES_DONT_MATCH_IB'] },
  { heading: 'Recommendations', types: ['ENTERED_SHORT_LIST'] },
];

/** Whether a typed number is one the server will take for this range. */
export function isAllowedThreshold(threshold: number | null, range: ThresholdRange): boolean {
  if (threshold === null || Number.isNaN(threshold)) return false;
  const withinRange = threshold >= range.smallest && threshold <= range.largest;
  return withinRange && (!range.wholeNumbersOnly || Number.isInteger(threshold));
}

/** "a whole number of days, 1–60" / "a percent, 1–100". */
export function allowedThresholdText(range: ThresholdRange): string {
  const what = range.wholeNumbersOnly ? `a whole number of ${range.unit}` : 'a percent';
  return `${what}, ${range.smallest}–${range.largest}`;
}

/** "Default: 7 days" / "Default: 15%" — under a sentence whose number isn't the default any more. */
export function defaultThresholdText(defaultThreshold: number, range: ThresholdRange): string {
  const unitText = range.unit === '%' ? '%' : ` ${range.unit}`;
  return `Default: ${defaultThreshold}${unitText}`;
}
