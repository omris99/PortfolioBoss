import { EMPTY_VALUE } from '../../lib/format';
import { HIGHLIGHTER_CLASS } from '../../lib/styles';
import type { CompanyInfo, EarningsDate } from '../../types/portfolio';

const MILLISECONDS_IN_A_DAY = 86_400_000;

/**
 * Finnhub's codes for when in the day the report comes — `bmo` before market open, `amc` after market close, `dmh`
 * during market hours —, in words; a code it doesn't send yet shows no hour.
 */
const EARNINGS_HOURS: Record<string, string> = {
  bmo: 'before the open',
  amc: 'after the close',
  dmh: 'during the session',
};

const TIMING_NOTE_HINT = 'A timing note, not a risk sign: it never lowers a stock.';

function earningsHourText(earningsDate: EarningsDate): string | null {
  return earningsDate.hour === null ? null : (EARNINGS_HOURS[earningsDate.hour] ?? null);
}

/** "2026-11-12, after the close". */
export function earningsText(earningsDate: EarningsDate): string {
  const hour = earningsHourText(earningsDate);
  return hour === null ? earningsDate.date : `${earningsDate.date}, ${hour}`;
}

/** Whole days from today to a 'yyyy-MM-dd' date, on the calendar: 0 today, 1 tomorrow, below 0 before today. */
function daysFromToday(isoDate: string): number {
  const [year, month, day] = isoDate.split('-').map(Number);
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((Date.UTC(year, month - 1, day) - today) / MILLISECONDS_IN_A_DAY);
}

/** "in 6 days", "tomorrow", "today" — or, for figures read days ago, "already due". */
function reportDayText(isoDate: string): string {
  const days = daysFromToday(isoDate);
  if (days < 0) return 'already due';
  if (days === 0) return 'today';
  if (days === 1) return 'tomorrow';
  return `in ${days} days`;
}

/** "Earnings in 6 days: wait for the report of 2026-10-30, after the close". */
function timingNoteText(earningsDate: EarningsDate): string {
  return `Earnings ${reportDayText(earningsDate.date)}: wait for the report of ${earningsText(earningsDate)}`;
}

/**
 * The timing note in full when the API counts the next report 0–14 days away, run over with the highlighter — a date to
 * look at, not a risk sign, so never in the warning tone; nothing otherwise.
 */
export function TimingNoteWhenDue({ companyInfo }: { companyInfo: CompanyInfo | null }) {
  const earningsDate = companyInfo?.nextEarningsDate ?? null;
  if (earningsDate === null || !companyInfo?.reportsWithin14Days) return null;
  return (
    <span title={TIMING_NOTE_HINT} className={`${HIGHLIGHTER_CLASS} box-decoration-clone text-caption`}>
      {timingNoteText(earningsDate)}
    </span>
  );
}

/** The Company tab's next report, in words — "2026-11-12, after the close" —; the timing note when it is due. */
export function NextEarnings({ companyInfo }: { companyInfo: CompanyInfo }) {
  const earningsDate = companyInfo.nextEarningsDate;
  if (earningsDate === null) return <span className="text-muted">none in the next four months</span>;
  if (companyInfo.reportsWithin14Days) return <TimingNoteWhenDue companyInfo={companyInfo} />;
  return <span>{earningsText(earningsDate)}</span>;
}

/** "in 6 days" with the highlighter, the timing note in full on hover. */
function ReportSoonMark({ earningsDate }: { earningsDate: EarningsDate }) {
  return (
    <span
      title={`${timingNoteText(earningsDate)}. ${TIMING_NOTE_HINT}`}
      className={`${HIGHLIGHTER_CLASS} whitespace-nowrap text-caption`}
    >
      {reportDayText(earningsDate.date)}
    </span>
  );
}

/**
 * The next report's date for a table cell or a line — its hour on hover, "—" without one —, and when the API counts it
 * 0–14 days away "in 6 days" with the highlighter: `below` the date in a table cell, `beside` it in a line.
 */
export function NextEarningsDate({
  companyInfo,
  markPlacement,
}: {
  companyInfo: CompanyInfo | null;
  markPlacement: 'below' | 'beside';
}) {
  if (companyInfo === null) return <span title="No answer from Finnhub">{EMPTY_VALUE}</span>;
  const earningsDate = companyInfo.nextEarningsDate;
  if (earningsDate === null) {
    return <span title="No report in the next four months, in Finnhub’s calendar">{EMPTY_VALUE}</span>;
  }
  const date = <span title={earningsText(earningsDate)}>{earningsDate.date}</span>;
  if (!companyInfo.reportsWithin14Days) return date;
  if (markPlacement === 'below') {
    return (
      <>
        {date}
        <div className="mt-1">
          <ReportSoonMark earningsDate={earningsDate} />
        </div>
      </>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5">
      {date}
      <ReportSoonMark earningsDate={earningsDate} />
    </span>
  );
}
