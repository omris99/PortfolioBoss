import { useRef, useState } from 'react';
import * as Popover from '@radix-ui/react-popover';
import { Bell } from 'lucide-react';
import { useAlerts } from '../../hooks/useAlerts';
import { markAlertsAsSeen, saveAlertSettings } from '../../lib/apiClient';
import { LARGE_ICON_BUTTON_CLASS } from '../../lib/styles';
import type { Alert, AlertSetting } from '../../types/alerts';
import { Hint } from '../Hint';
import { AlertSettingsDrawer } from './AlertSettingsDrawer';
import { AlertsPanel } from './AlertsPanel';

/** A number the badge can hold. */
const MOST_COUNT_SHOWN = 99;

/**
 * How far under the bell its panel opens, so that the panel starts 8px under the top bar (round 6) rather than over it:
 * the 32px bell is centred in the 56px bar, its bottom 12px above the bar's.
 */
const PANEL_OFFSET_UNDER_THE_BELL = (56 - 32) / 2 + 8;

/** The panel never comes closer than this to the screen's edge (at 1280–1440px the bell is nearer it than 200px). */
const PANEL_DISTANCE_FROM_THE_SCREEN_EDGE = 16;

function unseenAlertsOf(alerts: Alert[]): Alert[] {
  return alerts.filter((alert) => alert.seenAt === null);
}

/** "Alerts, 3 new" — the bell's name for a screen reader, since the badge is only a number. */
function bellLabelOf(unseenCount: number): string {
  return unseenCount === 0 ? 'Alerts' : `Alerts, ${unseenCount} new`;
}

/**
 * The alerts bell in the top bar, last, to the right of Sync now (MY_HOLDINGS_TODO.md, decision 15; the user's place for
 * it since 2026-11-25): a dot in the accent blue with the number of alerts
 * not seen yet — none, no dot. Opening it shows its panel (`AlertsPanel`) and marks every alert seen, so the number goes
 * back to none; the ones that were new keep a dot while the panel is open. ⚙ in the panel opens the settings drawer. It
 * loads its own alerts (`useAlerts`), again after every sync: `portfolioSyncedAt` is the portfolio's `asOf`.
 */
export function AlertsBell({ portfolioSyncedAt }: { portfolioSyncedAt: string | null }) {
  const { alerts, reload } = useAlerts(portfolioSyncedAt);
  const [isListOpen, setIsListOpen] = useState(false);
  const [newAlertIdsWhenOpened, setNewAlertIdsWhenOpened] = useState<Set<number>>(new Set());
  const [isSettingsDrawerOpen, setIsSettingsDrawerOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const shownAlerts = alerts?.alerts ?? [];

  /**
   * On opening, the focus goes to the panel itself, not to its first button (Radix's default): the ⚙'s hint opens on
   * the keyboard's focus, and showed "Alert settings" every time the panel opened. Tab still reaches the ⚙.
   */
  const focusThePanelItself = (event: Event) => {
    event.preventDefault();
    panelRef.current?.focus();
  };
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
        {/* No hint while the panel is open: it would cover the panel's head. */}
        <Hint text="Alerts" placement="below" align="end" focusable={false} disabled={isListOpen}>
          <Popover.Trigger
            aria-label={bellLabelOf(unseenAlerts.length)}
            className={`${LARGE_ICON_BUTTON_CLASS} relative data-[state=open]:bg-accent-soft data-[state=open]:text-accent`}
          >
            <Bell size={18} />
            {unseenAlerts.length > 0 && (
              // 7px out to the right, with a white ring (round 6) — the bar's side padding leaves it room.
              <span
                aria-hidden="true"
                className="absolute -right-[7px] -top-px flex h-4 min-w-[18px] items-center justify-center rounded-full bg-accent px-1 text-[11px] font-semibold leading-4 text-on-accent ring-2 ring-surface"
              >
                {unseenCountText}
              </span>
            )}
          </Popover.Trigger>
        </Hint>
        <Popover.Portal>
          <Popover.Content
            ref={panelRef}
            align="center"
            collisionPadding={PANEL_DISTANCE_FROM_THE_SCREEN_EDGE}
            sideOffset={PANEL_OFFSET_UNDER_THE_BELL}
            aria-label="Alerts"
            onOpenAutoFocus={focusThePanelItself}
            // Above the drawer (z-40), like the "?" popovers; centred under the bell, the bar's last (his ask), and
            // pushed back from the screen's edge where the bell is too close to it for the panel's half.
            className="popover-motion z-[60] w-[400px] max-w-[calc(100vw-32px)] overflow-hidden rounded-lg border bg-surface-raised text-ink shadow-raised focus:outline-none"
          >
            <AlertsPanel
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
