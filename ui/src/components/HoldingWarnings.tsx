import { TriangleAlert } from 'lucide-react';
import type { Holding, HoldingWarning } from '../types/portfolio';

const MINOR_COLOR_CLASS = 'text-yellow-400';
const ATTENTION_COLOR_CLASS = 'text-orange-500';

/**
 * "No buy entered yet" is expected on every holding still waiting to be backfilled, so it gets a milder colour than a
 * real gap in the data: in the same colour it would be on most rows at first, and the colour would soon stop meaning
 * anything.
 */
function isMinor(warning: HoldingWarning): boolean {
  return warning.type === 'NO_TRADES_LOGGED';
}

function colorClassOf(warnings: HoldingWarning[]): string {
  return warnings.every(isMinor) ? MINOR_COLOR_CLASS : ATTENTION_COLOR_CLASS;
}

/** A holding with a warning worth acting on: anything but the minor "no buy entered yet". */
export function needsAttention(holding: Holding): boolean {
  return !holding.warnings.every(isMinor);
}

/** The triangle next to the symbol; hovering shows the message. Nothing when the trades entered match IB. */
export function HoldingWarningIcon({ warnings }: { warnings: HoldingWarning[] }) {
  if (warnings.length === 0) return null;
  const messages = warnings.map((warning) => warning.message).join('\n');
  return (
    <span role="img" aria-label={messages} title={messages} className={colorClassOf(warnings)}>
      <TriangleAlert size={12} />
    </span>
  );
}

/** The same messages written out at the top of the trades panel, which is where they get fixed. */
export function HoldingWarningList({ warnings }: { warnings: HoldingWarning[] }) {
  if (warnings.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1">
      {warnings.map((warning) => (
        <li key={warning.type} className={`flex items-center gap-1.5 ${colorClassOf([warning])}`}>
          <TriangleAlert size={12} className="shrink-0" />
          {warning.message}
        </li>
      ))}
    </ul>
  );
}
