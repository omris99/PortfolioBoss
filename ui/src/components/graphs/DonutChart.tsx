import type { ReactNode } from 'react';

/** One part of the ring. */
export interface DonutSlice {
  key: string;
  /** How much of the ring it takes, in any unit: the slices are drawn in proportion to their sum. */
  amount: number;
  /** The Tailwind classes that paint it: a fill, and a stroke for an outlined one. */
  paintClass: string;
}

const SIZE = 168;
const CENTER = SIZE / 2;
/** One pixel short of the edge, so an outlined slice's stroke isn't cut off. */
const OUTER_RADIUS = CENTER - 1;
const INNER_RADIUS = 60;
/** The space between two slices, measured at the inner edge (a little wider at the outer). */
const GAP_BETWEEN_SLICES = 2;
const GAP_ANGLE = GAP_BETWEEN_SLICES / INNER_RADIUS;
/** A slice too small to see is still drawn a hair wide, so the legend's row has something to point at. */
const SMALLEST_DRAWN_ANGLE = 0.01;
const FULL_TURN = 2 * Math.PI;
/** Where the text in the hole starts: inside the inner circle, with a few pixels to spare. */
const HOLE_PADDING = CENTER - INNER_RADIUS + 6;

/** A point on a circle around the center; angle 0 is 12 o'clock, growing clockwise. */
function pointAt(angle: number, radius: number): string {
  return `${CENTER + radius * Math.sin(angle)} ${CENTER - radius * Math.cos(angle)}`;
}

/** A slice of the ring between two angles, clockwise: along the outer edge, then back along the inner one. */
function slicePath(startAngle: number, endAngle: number): string {
  const largeArcFlag = endAngle - startAngle > Math.PI ? 1 : 0;
  return [
    `M ${pointAt(startAngle, OUTER_RADIUS)}`,
    `A ${OUTER_RADIUS} ${OUTER_RADIUS} 0 ${largeArcFlag} 1 ${pointAt(endAngle, OUTER_RADIUS)}`,
    `L ${pointAt(endAngle, INNER_RADIUS)}`,
    `A ${INNER_RADIUS} ${INNER_RADIUS} 0 ${largeArcFlag} 0 ${pointAt(startAngle, INNER_RADIUS)}`,
    'Z',
  ].join(' ');
}

/** A whole circle as two half arcs, since one arc can't end where it starts. */
function circlePath(radius: number): string {
  const top = `${CENTER} ${CENTER - radius}`;
  const bottom = `${CENTER} ${CENTER + radius}`;
  return `M ${top} A ${radius} ${radius} 0 1 1 ${bottom} A ${radius} ${radius} 0 1 1 ${top} Z`;
}

/** The whole ring, for a single slice: the outer circle with the inner one cut out (`fillRule="evenodd"`). */
function fullRingPath(): string {
  return `${circlePath(OUTER_RADIUS)} ${circlePath(INNER_RADIUS)}`;
}

/** Each slice with the outline to draw, in order clockwise from 12 o'clock, a gap after each. */
function slicePathsOf(slices: DonutSlice[]): { slice: DonutSlice; path: string }[] {
  const drawnSlices = slices.filter((slice) => slice.amount > 0);
  if (drawnSlices.length === 1) return [{ slice: drawnSlices[0], path: fullRingPath() }];

  const totalAmount = drawnSlices.reduce((runningTotal, slice) => runningTotal + slice.amount, 0);
  const slicePaths: { slice: DonutSlice; path: string }[] = [];
  let sliceStartAngle = 0;
  for (const slice of drawnSlices) {
    const sliceAngle = (slice.amount / totalAmount) * FULL_TURN;
    const drawnStartAngle = sliceStartAngle + GAP_ANGLE / 2;
    const drawnAngle = Math.max(sliceAngle - GAP_ANGLE, SMALLEST_DRAWN_ANGLE);
    slicePaths.push({ slice, path: slicePath(drawnStartAngle, drawnStartAngle + drawnAngle) });
    sliceStartAngle += sliceAngle;
  }
  return slicePaths;
}

/**
 * A ring of slices, with `children` written in its hole. Pointing at a slice highlights it — the others fade — and
 * tells the owner through `onHighlight`, so a legend beside it can light the same row; the owner passes the highlighted
 * slice back as `highlightedKey`, which lets its legend's rows highlight the ring too. The ring itself is hidden from
 * screen readers: the legend beside it must name every slice.
 */
export function DonutChart({
  slices,
  highlightedKey,
  onHighlight,
  children,
}: {
  slices: DonutSlice[];
  highlightedKey: string | null;
  onHighlight: (key: string | null) => void;
  children: ReactNode;
}) {
  return (
    <div className="relative shrink-0" style={{ width: SIZE, height: SIZE }}>
      <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} aria-hidden="true">
        {slicePathsOf(slices).map(({ slice, path }) => {
          const isFaded = highlightedKey !== null && highlightedKey !== slice.key;
          return (
            <path
              key={slice.key}
              d={path}
              fillRule="evenodd"
              className={`transition-opacity ${slice.paintClass} ${isFaded ? 'opacity-40' : ''}`}
              onMouseEnter={() => onHighlight(slice.key)}
              onMouseLeave={() => onHighlight(null)}
            />
          );
        })}
      </svg>
      <div
        className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center text-center"
        style={{ padding: HOLE_PADDING }}
      >
        {children}
      </div>
    </div>
  );
}
