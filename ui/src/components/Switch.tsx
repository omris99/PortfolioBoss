import type { ReactNode } from 'react';

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
  const trackClass = checked ? 'bg-accent' : 'bg-surface shadow-[inset_0_0_0_1px_var(--color-border-strong)]';
  const thumbClass = checked ? 'left-[15px] bg-on-accent' : 'left-[3px] bg-[var(--color-text-muted)]';
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className="inline-flex h-8 items-center gap-2 whitespace-nowrap rounded-md px-1 text-ink"
    >
      <span aria-hidden="true" className={`relative h-4 w-7 rounded-full ${trackClass}`}>
        <span className={`absolute top-[3px] h-2.5 w-2.5 rounded-full ${thumbClass}`} />
      </span>
      {children}
    </button>
  );
}
