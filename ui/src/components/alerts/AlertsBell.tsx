import { useState } from 'react';
import * as Popover from '@radix-ui/react-popover';
import { Bell } from 'lucide-react';
import { useAlerts } from '../../hooks/useAlerts';
import { markAlertsAsSeen, saveAlertSettings } from '../../lib/apiClient';
import { LARGE_ICON_BUTTON_CLASS } from '../../lib/styles';
import type { Alert, AlertSetting } from '../../types/alerts';
import { AlertList } from './AlertList';
import { AlertSettingsDrawer } from './AlertSettingsDrawer';

/** A number the badge can hold. */
const MOST_COUNT_SHOWN = 99;

function unseenAlertsOf(alerts: Alert[]): Alert[] {
  return alerts.filter((alert) => alert.seenAt === null);
}

/** "Alerts, 3 new" — the bell's name for a screen reader, since the badge is only a number. */
function bellLabelOf(unseenCount: number): string {
  return unseenCount === 0 ? 'Alerts' : `Alerts, ${unseenCount} new`;
}

/**
 * The alerts bell in the top bar, first, to the left of the name (MY_HOLDINGS_TODO.md, decision 15; the user's place for
 * it, so that it is seen): a dot in the accent blue with the number of alerts
 * not seen yet — none, no dot. Opening it shows every alert and marks them seen, so the number goes back to none; the
 * ones that were new keep a dot while the list is open. ⚙ in the list opens the settings drawer. It loads its own alerts
 * (`useAlerts`), again after every sync: `portfolioSyncedAt` is the portfolio's `asOf`.
 */
export function AlertsBell({ portfolioSyncedAt }: { portfolioSyncedAt: string | null }) {
  const { alerts, reload } = useAlerts(portfolioSyncedAt);
  const [isListOpen, setIsListOpen] = useState(false);
  const [newAlertIdsWhenOpened, setNewAlertIdsWhenOpened] = useState<Set<number>>(new Set());
  const [isSettingsDrawerOpen, setIsSettingsDrawerOpen] = useState(false);
  const shownAlerts = alerts?.alerts ?? [];
  const unseenAlerts = unseenAlertsOf(shownAlerts);

  /** When marking fails (the API unreachable), the number stays, and the next opening tries again. */
  const markAsSeenAndReload = async () => {
    try {
      await markAlertsAsSeen();
      await reload();
    } catch {
      // the top bar already says when the API can't be reached
    }
  };

  const openOrCloseList = (isOpen: boolean) => {
    setIsListOpen(isOpen);
    if (!isOpen) return;
    setNewAlertIdsWhenOpened(new Set(unseenAlerts.map((alert) => alert.id)));
    if (unseenAlerts.length > 0) void markAsSeenAndReload();
  };

  const openSettingsDrawer = () => {
    setIsListOpen(false);
    setIsSettingsDrawerOpen(true);
  };

  const saveSettings = async (settings: AlertSetting[]) => {
    await saveAlertSettings(settings);
    await reload();
  };

  const unseenCountText = unseenAlerts.length > MOST_COUNT_SHOWN ? `${MOST_COUNT_SHOWN}+` : String(unseenAlerts.length);
  return (
    <>
      <Popover.Root open={isListOpen} onOpenChange={openOrCloseList}>
        <Popover.Trigger
          aria-label={bellLabelOf(unseenAlerts.length)}
          title="Alerts"
          className={`${LARGE_ICON_BUTTON_CLASS} relative data-[state=open]:bg-accent-soft data-[state=open]:text-accent`}
        >
          <Bell size={18} />
          {unseenAlerts.length > 0 && (
            <span
              aria-hidden="true"
              className="absolute -right-1 -top-1 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-accent px-1 text-[11px] font-semibold leading-none text-on-accent"
            >
              {unseenCountText}
            </span>
          )}
        </Popover.Trigger>
        <Popover.Portal>
          <Popover.Content
            align="start"
            sideOffset={8}
            aria-label="Alerts"
            // Above the drawer (z-40), like the "?" popovers.
            className="z-[60] w-[400px] max-w-[calc(100vw-32px)] overflow-hidden rounded-md border bg-surface-raised text-[13px] leading-[18px] shadow-raised"
          >
            <AlertList
              alerts={shownAlerts}
              newAlertIds={newAlertIdsWhenOpened}
              onOpenSettings={openSettingsDrawer}
              onAlertOpened={() => setIsListOpen(false)}
            />
          </Popover.Content>
        </Popover.Portal>
      </Popover.Root>
      {isSettingsDrawerOpen && alerts !== null && (
        <AlertSettingsDrawer
          settings={alerts.settings}
          onSave={saveSettings}
          onClose={() => setIsSettingsDrawerOpen(false)}
        />
      )}
    </>
  );
}
