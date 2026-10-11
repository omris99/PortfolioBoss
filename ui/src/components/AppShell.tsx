import type { ReactNode } from 'react';
import { CloudOff, Info, RefreshCw } from 'lucide-react';
import { Link, NavLink } from 'react-router';
import { SECONDARY_BUTTON_CLASS } from '../lib/styles';

export type LoadStatus = 'loading' | 'ready' | 'error';

/** The pages, in the order of the navigation. */
const NAVIGATION: { path: string; label: string }[] = [
  { path: '/', label: 'Overview' },
  { path: '/positions', label: 'Positions' },
  { path: '/closed', label: 'Closed' },
  { path: '/investors', label: 'Investors' },
];

const STATUS_DOT_CLASSES: Record<LoadStatus, string> = {
  loading: 'bg-status-loading',
  ready: 'bg-status-ready',
  error: 'bg-status-error',
};

const STATUS_TEXTS: Record<LoadStatus, string> = {
  loading: 'Loading…',
  ready: 'Data loaded',
  error: 'Could not reach the API',
};

/** The centred column every part of the page lines up to. */
const CONTAINER_CLASS = 'mx-auto w-full max-w-content px-8';

/** The current page's link in the accent colour, underlined on the bar's bottom edge. */
function navigationLinkClass({ isActive }: { isActive: boolean }): string {
  const baseClass = 'relative flex h-full items-center rounded-sm px-3 font-medium transition-colors';
  return isActive
    ? `${baseClass} text-accent after:absolute after:inset-x-3 after:-bottom-px after:h-0.5 after:rounded-t-sm after:bg-accent after:content-['']`
    : `${baseClass} text-muted hover:text-ink`;
}

/** The name, the four pages, and on the right the account, the last sync from TWS and whether the data loaded. */
function TopBar({
  account,
  syncedAt,
  loadStatus,
}: {
  account: string | null;
  syncedAt: string | null;
  loadStatus: LoadStatus;
}) {
  return (
    <header className="border-b bg-surface">
      <div className={`${CONTAINER_CLASS} flex h-topbar items-stretch gap-8`}>
        <Link to="/" className="flex items-center whitespace-nowrap text-wordmark text-ink">
          PortfolioBoss
        </Link>
        <nav aria-label="Main" className="flex">
          <ul className="flex items-stretch gap-1">
            {NAVIGATION.map((page) => (
              <li key={page.path} className="flex">
                {/* `end`: the Overview's "/" would otherwise count as current on every page. */}
                <NavLink to={page.path} end={page.path === '/'} className={navigationLinkClass}>
                  {page.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="ml-auto flex items-center gap-3 whitespace-nowrap text-caption text-muted">
          {account && (
            <>
              <span title="IB account" className="font-medium tracking-[0.01em] text-ink">
                {account}
              </span>
              <span aria-hidden="true" className="h-4 w-px bg-line" />
            </>
          )}
          {syncedAt ? (
            <span title="The data is as of this sync, not live.">
              Synced from TWS <b className="font-medium text-ink">{syncedAt}</b>
            </span>
          ) : (
            <span>{loadStatus === 'loading' ? 'Loading…' : 'Not loaded'}</span>
          )}
          <span role="status" title={STATUS_TEXTS[loadStatus]} className="inline-flex items-center">
            <span aria-hidden="true" className={`inline-block h-2 w-2 rounded-full ${STATUS_DOT_CLASSES[loadStatus]}`} />
            <span className="sr-only">{STATUS_TEXTS[loadStatus]}</span>
          </span>
        </div>
      </div>
    </header>
  );
}

/** Under the top bar while the API can't be reached; whatever loaded before stays on the page under it. */
function ErrorBanner({
  message,
  hasData,
  isRetrying,
  onRetry,
}: {
  message: string;
  hasData: boolean;
  isRetrying: boolean;
  onRetry: () => void;
}) {
  return (
    <div role="alert" className="border-b border-negative-line bg-negative-soft">
      <div className={`${CONTAINER_CLASS} flex min-h-[52px] items-center gap-3 py-2`}>
        <CloudOff size={20} className="shrink-0 text-negative" />
        <div className="flex-1">
          <span className="font-semibold">{message}</span>
          {hasData && <span className="text-muted"> The figures below are from the last load.</span>}
        </div>
        <button type="button" onClick={onRetry} disabled={isRetrying} className={SECONDARY_BUTTON_CLASS}>
          <RefreshCw size={16} />
          {isRetrying ? 'Retrying…' : 'Retry'}
        </button>
      </div>
    </div>
  );
}

function Footer() {
  return (
    <footer className="border-t">
      <div className={`${CONTAINER_CLASS} flex h-12 items-center gap-2 text-caption text-faint`}>
        <Info size={14} className="shrink-0" />
        Read-only against Interactive Brokers · sector and trades are stored locally
      </div>
    </footer>
  );
}

/**
 * The frame of every page: the top bar, the error banner when the API can't be reached, the page itself (`children`)
 * in the centred column, and the footer.
 */
export function AppShell({
  account,
  syncedAt,
  loadStatus,
  errorMessage,
  hasData,
  onRetry,
  children,
}: {
  account: string | null;
  syncedAt: string | null;
  loadStatus: LoadStatus;
  errorMessage: string | null;
  hasData: boolean;
  onRetry: () => void;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-full flex-col">
      <TopBar account={account} syncedAt={syncedAt} loadStatus={loadStatus} />
      {errorMessage && (
        <ErrorBanner
          message={errorMessage}
          hasData={hasData}
          isRetrying={loadStatus === 'loading'}
          onRetry={onRetry}
        />
      )}
      <main className="flex-1 pb-10 pt-6">
        <div className={CONTAINER_CLASS}>{children}</div>
      </main>
      <Footer />
    </div>
  );
}
