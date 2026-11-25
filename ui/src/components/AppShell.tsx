import type { ReactNode } from 'react';
import { CircleAlert, CloudOff, Info, RefreshCw } from 'lucide-react';
import type { ReadingFromTwsState } from '../hooks/useReadingFromTws';
import { SMALL_SECONDARY_BUTTON_CLASS } from '../lib/styles';
import { TopBar, type TopBarStatus } from './topBar/TopBar';

export type LoadStatus = 'loading' | 'ready' | 'error';

/**
 * The centred column every part of the page lines up to: 1200px wide up to a 1500px screen, then 80% of the screen, up to
 * 1536px (`max-w-content`, round 6 — so that on a 1920px screen the tables use the room).
 */
const CONTAINER_CLASS = 'mx-auto w-full max-w-content px-8';

/** What went wrong with the last Sync now — TWS not reachable, or another reading running —; `null` otherwise. */
function syncProblemMessageOf(syncNowState: ReadingFromTwsState): string | null {
  if (syncNowState.kind === 'busy' || syncNowState.kind === 'failed') {
    return syncNowState.message;
  }
  return null;
}

/** What the top bar says: an API that can't be reached first, then Sync now's reading, then the first load. */
function topBarStatusOf(loadStatus: LoadStatus, hasData: boolean, syncNowState: ReadingFromTwsState): TopBarStatus {
  if (loadStatus === 'error') return 'notConnected';
  if (syncNowState.kind === 'reading') return 'syncing';
  if (syncNowState.kind === 'failed') return 'syncFailed';
  if (!hasData) return 'loading';
  return 'ready';
}

/**
 * Under the top bar when the API can't be reached, or Sync now couldn't read TWS (round 6: a white band with a hairline
 * and the icon in the loss tone, not a pink fill — the only red fill is a Delete confirm): `message` says what, and
 * whatever loaded before stays on the page under it — `note` says so.
 */
function ErrorBanner({
  icon,
  message,
  note,
  retryButtonText,
  isRetrying,
  onRetry,
}: {
  icon: ReactNode;
  message: string;
  note: string | null;
  retryButtonText: string;
  isRetrying: boolean;
  onRetry: () => void;
}) {
  return (
    <div role="alert" className="border-b bg-surface">
      <div className={`${CONTAINER_CLASS} flex min-h-12 items-center gap-3 py-2`}>
        <span className="shrink-0 text-negative">{icon}</span>
        <div className="flex-1">
          <span className="font-semibold">{message}</span>
          {note !== null && <span className="text-muted"> {note}</span>}
        </div>
        <button type="button" onClick={onRetry} disabled={isRetrying} className={SMALL_SECONDARY_BUTTON_CLASS}>
          <RefreshCw size={14} />
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
        Read-only: PortfolioBoss never places an order with Interactive Brokers.
      </div>
    </footer>
  );
}

/**
 * The frame of every page: the top bar, an error banner when the API can't be reached or Sync now failed, the page
 * itself (`children`) in the centred column, and the footer. When the first data comes the column fades in once, over
 * the skeleton it replaces (round 6's Motion); moving from page to page later doesn't fade.
 *
 * @param portfolioSyncedAt the portfolio's `asOf`, as the API sends it, `null` before the first load
 */
export function AppShell({
  account,
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
        syncedAt={portfolioSyncedAt}
        status={topBarStatusOf(loadStatus, hasData, syncNowState)}
        syncNowState={syncNowState}
        onSyncNow={onSyncNow}
        containerClass={CONTAINER_CLASS}
      />
      {errorMessage && (
        <ErrorBanner
          icon={<CloudOff size={18} />}
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
          icon={<CircleAlert size={18} />}
          message={syncProblemMessage}
          note="The figures below are from the last sync."
          retryButtonText="Try again"
          isRetrying={false}
          onRetry={onSyncNow}
        />
      )}
      <main className="flex-1 pb-10 pt-6">
        {/* The class comes with the data, and the column stays, so its fade plays once. */}
        <div className={`${CONTAINER_CLASS} ${hasData ? 'fade-in-motion' : ''}`}>{children}</div>
      </main>
      <Footer />
    </div>
  );
}
