import type { ReactNode } from 'react';

/** The switch itself: a track with its thumb, the accent's when on. */
function SwitchTrack({ checked }: { checked: boolean }) {
  const trackClass = checked ? 'bg-accent' : 'bg-surface shadow-[inset_0_0_0_1px_var(--color-border-strong)]';
  const thumbClass = checked ? 'left-[15px] bg-on-accent' : 'left-[3px] bg-[var(--color-text-muted)]';
  return (
    <span aria-hidden="true" className={`relative h-4 w-7 shrink-0 rounded-full ${trackClass}`}>
      <span className={`absolute top-[3px] h-2.5 w-2.5 rounded-full ${thumbClass}`} />
    </span>
  );
}

/** An on/off switch with its label after it: "Show closed (1)". */
export function Switch({
  checked,
  onChange,
  children,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className="inline-flex h-8 items-center gap-2 whitespace-nowrap rounded-md px-1 text-ink"
    >
      <SwitchTrack checked={checked} />
      {children}
    </button>
  );
}

/**
 * A switch whose words stand beside it rather than inside it, named by them (`labelId`, the words' element): the alert
 * settings' sentences hold a number field, which can't be inside a button.
 */
export function SwitchNamedBy({
  checked,
  onChange,
  labelId,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  labelId: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-labelledby={labelId}
      onClick={() => onChange(!checked)}
      className="inline-flex h-8 items-center rounded-md px-1"
    >
      <SwitchTrack checked={checked} />
    </button>
  );
}
