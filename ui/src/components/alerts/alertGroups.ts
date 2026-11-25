import type { Alert, AlertGroup } from '../../types/alerts';

/** The words of one of the bell's two groups. */
export interface AlertGroupWords {
  group: AlertGroup;
  heading: string;
  /** The faint line of a group without any alert. */
  noneText: string;
}

/**
 * The bell's two groups in the panel's order (round 6, the design system's AlertsPanel): the holdings first, so that the
 * short list's many "Entered the short list" can't push them out of sight. Which group an alert is in comes from the
 * server.
 */
export const ALERT_GROUPS: AlertGroupWords[] = [
  { group: 'YOUR_HOLDINGS', heading: 'Your holdings', noneText: 'Nothing on your holdings.' },
  { group: 'SHORT_LIST', heading: 'The short list', noneText: 'Nothing on the short list.' },
];

/** How many alerts a group shows before "Show 28 more". */
export const ALERTS_SHOWN_PER_GROUP = 5;

/** A group's alerts, in the order the server sent them: the newest first. */
export function alertsOfGroup(alerts: Alert[], group: AlertGroup): Alert[] {
  return alerts.filter((alert) => alert.group === group);
}
