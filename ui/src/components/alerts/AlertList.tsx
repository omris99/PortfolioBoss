import { Settings } from 'lucide-react';
import { Link } from 'react-router';
import { formatDateTime } from '../../lib/format';
import { LARGE_ICON_BUTTON_CLASS } from '../../lib/styles';
import type { Alert } from '../../types/alerts';

/** A holding's alert opens its drawer; a scanned stock's, the Recommendations page. */
function addressOf(alert: Alert): string {
  return alert.holdingId === null ? '/recommendations' : `/positions/${alert.holdingId}`;
}

/** One alert: a blue dot when it was new as the bell opened, the symbol and what happened, and when. */
function AlertRow({ alert, isNew, onOpened }: { alert: Alert; isNew: boolean; onOpened: () => void }) {
  return (
    <li>
      <Link
        to={addressOf(alert)}
        onClick={onOpened}
        className="flex items-start gap-2.5 border-b px-4 py-2.5 transition-colors last:border-b-0 hover:bg-accent-soft"
      >
        <span
          aria-hidden="true"
          className={`mt-[7px] h-2 w-2 shrink-0 rounded-full ${isNew ? 'bg-accent' : 'bg-transparent'}`}
        />
        <span className="min-w-0 flex-1">
          <span className="block text-ink">
            <b className="font-semibold">{alert.symbol}</b> · {alert.message}
            {isNew && <span className="sr-only"> (new)</span>}
          </span>
          <span className="mt-0.5 block text-caption text-faint">{formatDateTime(alert.createdAt)}</span>
        </span>
      </Link>
    </li>
  );
}

/**
 * The bell's list (MY_HOLDINGS_TODO.md, decision 15): every alert of the types turned on, the newest first, with ⚙ for
 * the settings in its header.
 *
 * @param newAlertIds the alerts not seen yet when the bell was opened — opening marked them seen, but they keep their
 *                    dot until the list closes
 */
export function AlertList({
  alerts,
  newAlertIds,
  onOpenSettings,
  onAlertOpened,
}: {
  alerts: Alert[];
  newAlertIds: Set<number>;
  onOpenSettings: () => void;
  onAlertOpened: () => void;
}) {
  return (
    <div className="flex max-h-[480px] flex-col">
      <div className="flex items-center gap-2 border-b px-4 py-2">
        <h2 className="font-semibold">Alerts</h2>
        <button
          type="button"
          aria-label="Alert settings"
          title="Alert settings"
          onClick={onOpenSettings}
          className={`${LARGE_ICON_BUTTON_CLASS} ml-auto -mr-2`}
        >
          <Settings size={16} />
        </button>
      </div>
      {alerts.length === 0 ? (
        <p className="px-4 py-6 text-center text-muted">No alerts yet. They are checked on every sync.</p>
      ) : (
        <ul className="overflow-y-auto">
          {alerts.map((alert) => (
            <AlertRow key={alert.id} alert={alert} isNew={newAlertIds.has(alert.id)} onOpened={onAlertOpened} />
          ))}
        </ul>
      )}
    </div>
  );
}
