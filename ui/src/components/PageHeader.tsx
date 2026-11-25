import type { ReactNode } from 'react';

/**
 * The top of a page: its title, the line under it (`description`: what the page shows), and its actions on the right
 * (`children`). Every page has the line (round 6), so the first card sits at the same height on every page — moving
 * from page to page doesn't make it jump.
 */
export function PageHeader({
  title,
  description,
  children,
}: {
  title: string;
  description: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-page-title">{title}</h1>
        <div className="mt-0.5 text-muted">{description}</div>
      </div>
      {children && <div className="flex flex-wrap items-center gap-4">{children}</div>}
    </div>
  );
}
