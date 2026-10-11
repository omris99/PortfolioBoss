import type { Holding, HoldingWarning } from '../types/portfolio';
import { WarnMark, WarningBand, type WarningLevel } from './WarnMark';

/**
 * "No buy entered yet" is expected on every holding still waiting to be backfilled, so it is the minor level: as a real
 * warning it would be on most rows at first, and the warning would soon stop meaning anything.
 */
function levelOf(warning: HoldingWarning): WarningLevel {
  return warning.type === 'NO_TRADES_LOGGED' ? 'minor' : 'real';
}

function areAllMinor(warnings: HoldingWarning[]): boolean {
  return warnings.every((warning) => levelOf(warning) === 'minor');
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
  return holding.warnings.find((warning) => levelOf(warning) === 'real') ?? null;
}

/** The triangle next to the symbol; hovering shows the message. Nothing when the trades entered match IB. */
export function HoldingWarningMark({ warnings }: { warnings: HoldingWarning[] }) {
  if (warnings.length === 0) return null;
  const messages = warnings.map((warning) => warning.message).join('\n');
  return <WarnMark level={areAllMinor(warnings) ? 'minor' : 'real'} title={messages} />;
}

/** The same messages written out, at the top of a holding's drawer and of its Trades tab. */
export function HoldingWarningBands({ warnings }: { warnings: HoldingWarning[] }) {
  if (warnings.length === 0) return null;
  return (
    <div className="flex flex-col gap-2">
      {warnings.map((warning) => (
        <WarningBand key={warning.type} level={levelOf(warning)}>
          {warning.message}
        </WarningBand>
      ))}
    </div>
  );
}
