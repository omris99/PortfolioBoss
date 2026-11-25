/**
 * The first load (round 6, the design system's FirstLoad card): every word known before the data — a page's title, a
 * card's title, the labels, a table's head, the tabs — shows at once, and only the figures wait, each as a grey bar in
 * the place it will take. No shimmer; when the data comes it fades in, in the same places.
 */

/**
 * A figure not loaded yet: a grey bar about as tall as the text it stands for (12px for 14px text), `width` in px or a
 * percent. It sits inside the element that will hold the figure, with the figure's own text size, so the line keeps its
 * height and nothing moves when the figure replaces it.
 */
export function SkeletonBar({ width, height }: { width: number | string; height: number }) {
  return (
    <span
      aria-hidden="true"
      className="inline-block shrink-0 rounded-sm bg-subtle align-middle"
      style={{ width, height }}
    />
  );
}

/** The signal dot not loaded yet: a grey dot of its size. */
export function SkeletonDot({ size }: { size: number }) {
  return (
    <span
      aria-hidden="true"
      className="inline-block shrink-0 rounded-full bg-subtle align-middle"
      style={{ width: size, height: size }}
    />
  );
}
