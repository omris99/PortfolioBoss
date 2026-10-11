import { TriangleAlert } from 'lucide-react';
import type { Holding, HoldingWarning } from '../types/portfolio';

/** The triangle's colour: a minor warning's is a little lighter than its text, a real one's the same. */
const MINOR_ICON_COLOR_CLASS = 'text-warn-minor-icon';
const MINOR_TEXT_COLOR_CLASS = 'text-warn-minor';
const REAL_COLOR_CLASS = 'text-warn-real';

/**
 * "No buy entered yet" is expected on every holding still waiting to be backfilled, so it gets a milder colour than a
 * real gap in the data: in the same colour it would be on most rows at first, and the colour would soon stop meaning
 * anything.
 */
function isMinor(warning: HoldingWarning): boolean {
  return warning.type === 'NO_TRADES_LOGGED';
}

function areAllMinor(warnings: HoldingWarning[]): boolean {
  return warnings.every(isMinor);
}

/** A holding with a warning worth acting on: anything but the minor "no buy entered yet". */
export function needsAttention(holding: Holding): boolean {
  return !areAllMinor(holding.warnings);
}

/** A holding whose only warnings are the minor "no buy entered yet": counted on the Overview, not listed. */
export function hasOnlyMinorWarnings(holding: Holding): boolean {
  return holding.warnings.length > 0 && areAllMinor(holding.warnings);
}

/** The first warning worth acting on, or `null`: what the Overview says about the holding. */
export function firstRealWarningOf(holding: Holding): HoldingWarning | null {
  return holding.warnings.find((warning) => !isMinor(warning)) ?? null;
}

/** The triangle next to the symbol; hovering shows the message. Nothing when the trades entered match IB. */
export function HoldingWarningIcon({ warnings }: { warnings: HoldingWarning[] }) {
  if (warnings.length === 0) return null;
  const messages = warnings.map((warning) => warning.message).join('\n');
  const colorClass = areAllMinor(warnings) ? MINOR_ICON_COLOR_CLASS : REAL_COLOR_CLASS;
  return (
    <span role="img" aria-label={messages} title={messages} className={colorClass}>
      <TriangleAlert size={14} />
    </span>
  );
}

/** The same messages written out at the top of the trades panel, which is where they get fixed. */
export function HoldingWarningList({ warnings }: { warnings: HoldingWarning[] }) {
  if (warnings.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1">
      {warnings.map((warning) => (
        <li
          key={warning.type}
          className={`flex items-center gap-2 ${isMinor(warning) ? MINOR_TEXT_COLOR_CLASS : REAL_COLOR_CLASS}`}
        >
          <TriangleAlert size={14} className={`shrink-0 ${isMinor(warning) ? MINOR_ICON_COLOR_CLASS : ''}`} />
          {warning.message}
        </li>
      ))}
    </ul>
  );
}
