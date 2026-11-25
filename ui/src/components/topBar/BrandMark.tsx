/**
 * The app's mark — the wordmark's "P" drawn as a stroke on an accent square (DESIGN_BRIEF.md §11, round 6) — before the
 * name in the top bar. `public/favicon.svg` is the same drawing, with the colors written in: change both together.
 */
export function BrandMark({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true" className="block shrink-0">
      <rect width="32" height="32" rx="7" className="fill-accent" />
      <path
        d="M11.5 24V8.5h5.75a4.75 4.75 0 0 1 0 9.5H11.5"
        fill="none"
        strokeWidth="3.4"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="stroke-on-accent"
      />
    </svg>
  );
}
