import type { ReactNode } from 'react';
import { CircleAlert, CloudOff } from 'lucide-react';
import { Link, NavLink } from 'react-router';
import type { ReadingFromTwsState } from '../../hooks/useReadingFromTws';
import { formatDateTime } from '../../lib/format';
import { AlertsBell } from '../alerts/AlertsBell';
import { Hint } from '../Hint';
import { ReadingFromTwsButton } from '../ReadingFromTwsButton';
import { BrandMark } from './BrandMark';
import { syncSentence } from './syncSentence';
import { useCurrentMinute } from './useCurrentMinute';

/**
 * What the bar says about the figures, in words (round 6: no colored dot — green is profit and the signal's):
 * - `loading`: the first load, nothing known yet
 * - `ready`: when the shown figures were synced
 * - `syncing`: Sync now is reading TWS
 * - `syncFailed`: Sync now couldn't read TWS — the band under the bar says why
 * - `notConnected`: the API can't be reached — the band under the bar says so
 */
export type TopBarStatus = 'loading' | 'ready' | 'syncing' | 'syncFailed' | 'notConnected';

/** The pages, in the order of the navigation. Each page's own title keeps its full name ("Averaging calculator"). */
const NAVIGATION: { path: string; label: string }[] = [
  { path: '/', label: 'Overview' },
  { path: '/positions', label: 'Positions' },
  { path: '/averaging', label: 'Averaging' },
  { path: '/closed', label: 'Closed' },
  { path: '/investors', label: 'Investors' },
  { path: '/recommendations', label: 'Recommendations' },
  { path: '/taxes', label: 'Taxes' },
];

/** The current page's link in the accent color, underlined on the bar's bottom edge; a link never wraps. */
function navigationLinkClass({ isActive }: { isActive: boolean }): string {
  const baseClass = 'relative flex h-full items-center whitespace-nowrap rounded-sm px-2.5 font-medium transition-colors';
  return isActive
    ? `${baseClass} text-accent after:absolute after:inset-x-2.5 after:-bottom-px after:h-0.5 after:rounded-t-sm after:bg-accent after:content-['']`
    : `${baseClass} text-muted hover:text-ink`;
}

const SYNC_NOW_HINT = 'Read the portfolio from TWS again, as ./run.sh does at startup. TWS must be open.';

/** "Sync failed" and "Not connected": the loss tone, with their icon. */
function ProblemWords({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 font-medium text-negative">
      {icon}
      {children}
    </span>
  );
}

/** The slot's words for each status, and the hint that says more — `null` when there is nothing more to say. */
function syncLineOf(
  status: TopBarStatus,
  syncedAt: string | null,
  account: string | null,
  now: Date,
): { words: ReactNode; hint: string | null } {
  const syncTime = syncedAt === null ? null : formatDateTime(syncedAt);
  switch (status) {
    case 'loading':
      return { words: <span className="text-faint">Loading…</span>, hint: null };
    case 'syncing':
      return {
        words: 'Syncing with TWS…',
        hint: `Getting the account from TWS.${syncTime === null ? '' : ` The figures are from ${syncTime} until it ends.`}`,
      };
    case 'syncFailed':
      return {
        words: <ProblemWords icon={<CircleAlert size={14} aria-hidden="true" />}>Sync failed</ProblemWords>,
        hint: `TWS could not be reached.${syncTime === null ? '' : ` The figures are from the sync of ${syncTime}.`}`,
      };
    case 'notConnected':
      return {
        words: <ProblemWords icon={<CloudOff size={14} aria-hidden="true" />}>Not connected</ProblemWords>,
        hint: 'The PortfolioBoss API could not be reached. The figures are from the last load.',
      };
    case 'ready':
      return {
        words: syncedAt === null ? 'Not synced yet' : syncSentence(new Date(syncedAt), now),
        hint:
          syncTime === null ? null : `From TWS, ${syncTime}${account === null ? '' : ` · account ${account}`}`,
      };
  }
}

/**
 * When the figures were read from TWS, in a slot of a fixed width (168px, its words at its right edge), so that the bar
 * doesn't move between the first load and the data; the exact time and the account in its hint.
 */
function SyncLine({
  status,
  syncedAt,
  account,
}: {
  status: TopBarStatus;
  syncedAt: string | null;
  account: string | null;
}) {
  const now = useCurrentMinute();
  const { words, hint } = syncLineOf(status, syncedAt, account, now);
  return (
    <span role="status" className="mr-2 inline-flex min-w-[168px] justify-end">
      {hint === null ? (
        words
      ) : (
        <Hint text={hint} placement="below" align="end">
          {words}
        </Hint>
      )}
    </span>
  );
}

/**
 * The bar on every page (DESIGN_BRIEF.md §11, round 6): the alerts bell left of the name (the user's place for it), the
 * mark and the name, the seven pages — on one line from 1280px —, then on the right when the figures were synced, in
 * words, and Sync now.
 *
 * @param syncedAt the portfolio's `asOf`, as the API sends it; the bell loads its alerts again when it changes
 */
export function TopBar({
  account,
  syncedAt,
  status,
  syncNowState,
  onSyncNow,
  containerClass,
}: {
  account: string | null;
  syncedAt: string | null;
  status: TopBarStatus;
  syncNowState: ReadingFromTwsState;
  onSyncNow: () => void;
  containerClass: string;
}) {
  return (
    // Always on screen (the user's ask): a drawer opens right under it at any scroll, rather than floating with a gap.
    <header className="sticky top-0 z-30 border-b bg-surface">
      <div className={`${containerClass} flex h-topbar items-stretch gap-6`}>
        <div className="flex items-center gap-6">
          <AlertsBell portfolioSyncedAt={syncedAt} />
          <Link to="/" className="flex items-center gap-2 whitespace-nowrap text-wordmark text-ink">
            <BrandMark size={20} />
            PortfolioBoss
          </Link>
        </div>
        <nav aria-label="Main" className="flex min-w-0">
          <ul className="flex items-stretch gap-0.5">
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
        <div className="ml-auto flex items-center gap-2 whitespace-nowrap text-[13px] leading-[18px] text-muted">
          <SyncLine status={status} syncedAt={syncedAt} account={account} />
          <Hint text={SYNC_NOW_HINT} placement="below" align="end" focusable={false}>
            <ReadingFromTwsButton
              readingState={syncNowState}
              idleText="Sync now"
              readingText="Syncing…"
              size="small"
              disabled={status === 'loading'}
              className="min-w-[104px]"
              onClick={onSyncNow}
            />
          </Hint>
        </div>
      </div>
    </header>
  );
}
