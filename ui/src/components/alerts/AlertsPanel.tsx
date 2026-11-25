import { useState } from 'react';
import { BellOff, ChevronDown, Settings } from 'lucide-react';
import { Link } from 'react-router';
import { LARGE_ICON_BUTTON_CLASS, LINK_BUTTON_CLASS } from '../../lib/styles';
import type { Alert } from '../../types/alerts';
import { useCurrentMinute } from '../topBar/useCurrentMinute';
import { ALERT_GROUPS, ALERTS_SHOWN_PER_GROUP, alertsOfGroup, type AlertGroupWords } from './alertGroups';
import { alertTimeText } from './alertTime';

/** A holding's alert opens its drawer; a scanned stock's, the Recommendations page. */
function addressOf(alert: Alert): string {
  return alert.holdingId === null ? '/recommendations' : `/positions/${alert.holdingId}`;
}

/**
 * One alert in two lines: the symbol and when, then what happened. New — not seen yet when the bell opened —: the blue
 * dot and the words in full ink; seen: no dot, all of it muted.
 */
function AlertRow({
  alert,
  isNew,
  now,
  onOpened,
}: {
  alert: Alert;
  isNew: boolean;
  now: Date;
  onOpened: () => void;
}) {
  return (
    <li>
      <Link
        to={addressOf(alert)}
        onClick={onOpened}
        // The ring inside the row: the list scrolls, and a ring outside it would be cut.
        className="grid grid-cols-[8px_minmax(0,1fr)_auto] items-baseline gap-x-2 px-4 py-2 transition-colors hover:bg-subtle focus-visible:shadow-[inset_0_0_0_2px_var(--color-accent)]"
      >
        <span aria-hidden="true" className={`h-2 w-2 self-center rounded-full ${isNew ? 'bg-accent' : ''}`} />
        <span className={isNew ? 'font-semibold text-ink' : 'font-medium text-muted'}>
          {alert.symbol}
          {isNew && <span className="sr-only"> (new)</span>}
        </span>
        <span className="text-caption tabular-nums text-faint">{alertTimeText(alert.createdAt, now)}</span>
        <span className={`col-span-2 col-start-2 text-[13px] leading-[18px] ${isNew ? 'text-ink' : 'text-muted'}`}>
          {alert.message}
        </span>
      </Link>
    </li>
  );
}

/**
 * One group: its heading with its count — staying at the top while its list scrolls under it —, its five newest alerts
 * and "Show 28 more", which opens the rest in place; a faint line when it has none.
 */
function AlertGroupSection({
  words,
  alerts,
  newAlertIds,
  now,
  onAlertOpened,
}: {
  words: AlertGroupWords;
  alerts: Alert[];
  newAlertIds: Set<number>;
  now: Date;
  onAlertOpened: () => void;
}) {
  const [showsEveryAlert, setShowsEveryAlert] = useState(false);
  const shownAlerts = showsEveryAlert ? alerts : alerts.slice(0, ALERTS_SHOWN_PER_GROUP);
  const hiddenCount = alerts.length - shownAlerts.length;
  return (
    <section aria-label={words.heading} className="border-t first:border-t-0">
      <h3 className="sticky top-0 z-[1] flex items-center gap-2 bg-surface-raised px-4 pb-1 pt-3 text-caption font-semibold text-muted">
        {words.heading}
        <span className="font-medium tabular-nums text-faint">{alerts.length}</span>
      </h3>
      {alerts.length === 0 ? (
        <p className="px-4 pb-2 pt-1 text-[13px] text-faint">{words.noneText}</p>
      ) : (
        <ul>
          {shownAlerts.map((alert) => (
            <AlertRow
              key={alert.id}
              alert={alert}
              isNew={newAlertIds.has(alert.id)}
              now={now}
              onOpened={onAlertOpened}
            />
          ))}
        </ul>
      )}
      {hiddenCount > 0 && (
        <button type="button" onClick={() => setShowsEveryAlert(true)} className={`${LINK_BUTTON_CLASS} mx-4 mb-2 mt-1`}>
          Show {hiddenCount} more
          <ChevronDown size={14} aria-hidden="true" />
        </button>
      )}
    </section>
  );
}

function NoAlertsYet() {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-8 text-center text-faint">
      <BellOff size={20} aria-hidden="true" />
      <p className="text-body text-muted">No alerts yet. They are checked on every sync.</p>
    </div>
  );
}

/**
 * What the bell opens (round 6, the design system's AlertsPanel): "Alerts", how many are new and ⚙ for the settings,
 * then the alerts of the types turned on in two groups — Your holdings, then The short list —, each the newest first. At
 * most 600px tall (or the window less 80px): only the groups scroll.
 *
 * @param newAlertIds the alerts not seen yet when the bell was opened — opening marked them seen, but they stay new until
 *                    the panel closes
 */
export function AlertsPanel({
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
  const now = useCurrentMinute();
  return (
    <div className="flex max-h-[min(600px,calc(100vh_-_80px))] flex-col">
      <div className="flex shrink-0 items-center gap-2 border-b py-2 pl-4 pr-3">
        <h2 className="text-section-title">Alerts</h2>
        {newAlertIds.size > 0 && <span className="text-caption tabular-nums text-muted">{newAlertIds.size} new</span>}
        <button
          type="button"
          aria-label="Alert settings"
          title="Alert settings"
          onClick={onOpenSettings}
          className={`${LARGE_ICON_BUTTON_CLASS} ml-auto`}
        >
          <Settings size={16} />
        </button>
      </div>
      {alerts.length === 0 ? (
        <NoAlertsYet />
      ) : (
        <div className="min-h-0 overflow-y-auto overscroll-contain pb-2">
          {ALERT_GROUPS.map((words) => (
            <AlertGroupSection
              key={words.group}
              words={words}
              alerts={alertsOfGroup(alerts, words.group)}
              newAlertIds={newAlertIds}
              now={now}
              onAlertOpened={onAlertOpened}
            />
          ))}
        </div>
      )}
    </div>
  );
}
