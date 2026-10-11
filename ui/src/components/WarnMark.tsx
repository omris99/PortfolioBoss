import type { ReactNode } from 'react';

/**
 * How much a warning matters: `minor` is expected while trades are still being entered ("No buy entered yet"), `real` is
 * a gap to fix. The two are told apart by shape and lightness, not by hue alone, so they hold up in grayscale too.
 */
export type WarningLevel = 'minor' | 'real';

const TRIANGLE_CLASSES: Record<WarningLevel, string> = {
  minor: 'fill-warn-minor stroke-warn-minor-edge',
  real: 'fill-warn-real stroke-warn-real',
};

const EXCLAMATION_CLASSES: Record<WarningLevel, { line: string; dot: string }> = {
  minor: { line: 'stroke-ink', dot: 'fill-ink' },
  real: { line: 'stroke-on-warn-real', dot: 'fill-on-warn-real' },
};

/**
 * The warning triangle: a bright yellow one with a dark outline and "!" for a minor warning, a solid orange one with a
 * white "!" for a real one. `title` is the warning's message, on hover and to screen readers.
 */
export function WarnMark({ level, title, size = 16 }: { level: WarningLevel; title?: string; size?: number }) {
  const label = title ?? (level === 'real' ? 'Warning' : 'Minor warning');
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" role="img" aria-label={label} className="inline-block shrink-0 overflow-visible">
      {title && <title>{title}</title>}
      <path
        d="M7.13 1.95a1 1 0 0 1 1.74 0l6.3 11.05a1 1 0 0 1-.87 1.5H1.7a1 1 0 0 1-.87-1.5z"
        strokeWidth={1}
        strokeLinejoin="round"
        className={TRIANGLE_CLASSES[level]}
      />
      <path d="M8 5.6v3.9" strokeWidth={1.6} strokeLinecap="round" className={EXCLAMATION_CLASSES[level].line} />
      <circle cx={8} cy={11.85} r={0.95} className={EXCLAMATION_CLASSES[level].dot} />
    </svg>
  );
}

/** The warning's sentence on its level's soft ground, with the triangle: at the top of a drawer and its Trades tab. */
export function WarningBand({ level, children }: { level: WarningLevel; children: ReactNode }) {
  const levelClass = level === 'real' ? 'bg-warn-real-soft font-medium' : 'bg-warn-minor-soft';
  return (
    <div role="note" className={`flex items-start gap-2 rounded-md px-3 py-2 text-ink ${levelClass}`}>
      <span className="mt-0.5 flex">
        <WarnMark level={level} />
      </span>
      <span>{children}</span>
    </div>
  );
}
