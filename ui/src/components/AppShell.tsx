import type { ReactNode } from 'react';
import { CloudOff, Info, RefreshCw } from 'lucide-react';
import { Link, NavLink } from 'react-router';
import type { ReadingFromTwsState } from '../hooks/useReadingFromTws';
import { SECONDARY_BUTTON_CLASS } from '../lib/styles';
import { AlertsBell } from './alerts/AlertsBell';
import { ReadingFromTwsButton } from './ReadingFromTwsButton';

export type LoadStatus = 'loading' | 'ready' | 'error';

/** The pages, in the order of the navigation. */
const NAVIGATION: { path: string; label: string }[] = [
  { path: '/', label: 'Overview' },
  { path: '/positions', label: 'Positions' },
  { path: '/averaging', label: 'Averaging calculator' },
  { path: '/closed', label: 'Closed' },
  { path: '/investors', label: 'Investors' },
  { path: '/recommendations', label: 'Recommendations' },
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

/** What went wrong with the last Sync now — TWS not reachable, or another reading running —; `null` otherwise. */
function syncProblemMessageOf(syncNowState: ReadingFromTwsState): string | null {
  if (syncNowState.kind === 'busy' || syncNowState.kind === 'failed') {
    return syncNowState.message;
  }
  return null;
}

/**
 * The alerts bell with the name after it, the pages, and on the right the account, the last sync from TWS with Sync
 * now, and whether the data loaded.
 *
 * @param portfolioSyncedAt the portfolio's `asOf`, as the API sends it: the bell loads its alerts again when it changes
 */
function TopBar({
  account,
  syncedAt,
  portfolioSyncedAt,
  loadStatus,
  syncNowState,
  onSyncNow,
}: {
  account: string | null;
  syncedAt: string | null;
  portfolioSyncedAt: string | null;
  loadStatus: LoadStatus;
  syncNowState: ReadingFromTwsState;
  onSyncNow: () => void;
}) {
  return (
    <header className="border-b bg-surface">
      {/* gap-4 (16px): with a tab's own 12px padding, the first tab sits as far from the name as the tabs from each
          other (12 + 4 + 12). */}
      <div className={`${CONTAINER_CLASS} flex h-topbar items-stretch gap-4`}>
        <div className="flex items-center gap-1.5">
          <AlertsBell portfolioSyncedAt={portfolioSyncedAt} />
          <Link to="/" className="flex items-center whitespace-nowrap text-wordmark text-ink">
            PortfolioBoss
          </Link>
        </div>
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
          <ReadingFromTwsButton
            readingState={syncNowState}
            idleText="Sync now"
            readingText="Syncing…"
            size="small"
            title="Read the portfolio from TWS again, as ./run.sh does at startup. TWS must be open."
            onClick={onSyncNow}
          />
          <span role="status" title={STATUS_TEXTS[loadStatus]} className="inline-flex items-center">
            <span aria-hidden="true" className={`inline-block h-2 w-2 rounded-full ${STATUS_DOT_CLASSES[loadStatus]}`} />
            <span className="sr-only">{STATUS_TEXTS[loadStatus]}</span>
          </span>
        </div>
      </div>
    </header>
  );
}

/**
 * Under the top bar when the API can't be reached, or Sync now couldn't read TWS: `message` says what, and whatever
 * loaded before stays on the page under it — `note` says so.
 */
function ErrorBanner({
  message,
  note,
  retryButtonText,
  isRetrying,
  onRetry,
}: {
  message: string;
  note: string | null;
  retryButtonText: string;
  isRetrying: boolean;
  onRetry: () => void;
}) {
  return (
    <div role="alert" className="border-b border-negative-line bg-negative-soft">
      <div className={`${CONTAINER_CLASS} flex min-h-[52px] items-center gap-3 py-2`}>
        <CloudOff size={20} className="shrink-0 text-negative" />
        <div className="flex-1">
          <span className="font-semibold">{message}</span>
          {note !== null && <span className="text-muted"> {note}</span>}
        </div>
        <button type="button" onClick={onRetry} disabled={isRetrying} className={SECONDARY_BUTTON_CLASS}>
          <RefreshCw size={16} />
          {retryButtonText}
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
 * The frame of every page: the top bar, an error banner when the API can't be reached or Sync now failed, the page
 * itself (`children`) in the centred column, and the footer.
 */
export function AppShell({
  account,
  syncedAt,
  portfolioSyncedAt,
  loadStatus,
  errorMessage,
  hasData,
  onRetry,
  syncNowState,
  onSyncNow,
  children,
}: {
  account: string | null;
  syncedAt: string | null;
  portfolioSyncedAt: string | null;
  loadStatus: LoadStatus;
  errorMessage: string | null;
  hasData: boolean;
  onRetry: () => void;
  syncNowState: ReadingFromTwsState;
  onSyncNow: () => void;
  children: ReactNode;
}) {
  const isRetrying = loadStatus === 'loading';
  const syncProblemMessage = syncProblemMessageOf(syncNowState);
  return (
    <div className="flex min-h-full flex-col">
      <TopBar
        account={account}
        syncedAt={syncedAt}
        portfolioSyncedAt={portfolioSyncedAt}
        loadStatus={loadStatus}
        syncNowState={syncNowState}
        onSyncNow={onSyncNow}
      />
      {errorMessage && (
        <ErrorBanner
          message={errorMessage}
          note={hasData ? 'The figures below are from the last load.' : null}
          retryButtonText={isRetrying ? 'Retrying…' : 'Retry'}
          isRetrying={isRetrying}
          onRetry={onRetry}
        />
      )}
      {/* While Sync now runs again its state is 'reading', so the banner is gone and the button shows "Syncing…". */}
      {syncProblemMessage !== null && (
        <ErrorBanner
          message={syncProblemMessage}
          note="The figures below are from the last sync."
          retryButtonText="Try again"
          isRetrying={false}
          onRetry={onSyncNow}
        />
      )}
      <main className="flex-1 pb-10 pt-6">
        <div className={CONTAINER_CLASS}>{children}</div>
      </main>
      <Footer />
    </div>
  );
}
