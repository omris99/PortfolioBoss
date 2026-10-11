import type { ReactNode } from 'react';

/**
 * The top of a page: its title, a line under it (`description`: what the page shows, or what needs a look), and its
 * actions on the right (`children`).
 */
export function PageHeader({
  title,
  description,
  children,
}: {
  title: string;
  description?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-page-title">{title}</h1>
        {description && <div className="mt-1 text-muted">{description}</div>}
      </div>
      {children && <div className="flex flex-wrap items-center gap-4">{children}</div>}
    </div>
  );
}
