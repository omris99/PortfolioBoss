/**
 * A short explanation in a small bubble above what it explains: the design system's Tip, in place of the browser's own
 * tooltip. It keeps no state of its own. Its parent says when it is open (while the row is pointed at or has the
 * keyboard's focus), is `relative` so that the bubble sits over it, and points to it with `aria-describedby={id}` so that
 * a screen reader reads it too. Only the weight graphs use it for now; the rest of the app still uses the browser's.
 */
export function Tip({ id, text, isOpen }: { id: string; text: string; isOpen: boolean }) {
  return (
    <span
      id={id}
      role="tooltip"
      className={`pointer-events-none absolute bottom-full left-1/2 z-30 mb-1.5 w-max max-w-[300px] -translate-x-1/2 rounded-md border bg-surface-raised px-2.5 py-1.5 text-left text-caption font-normal text-ink shadow-raised ${
        isOpen ? 'block' : 'hidden'
      }`}
    >
      {text}
    </span>
  );
}
