import type { ReactNode } from 'react';
import { Check, Info } from 'lucide-react';
import { formatDayCount, formatShekels } from '../../lib/format';
import { CARD_CLASS } from '../../lib/styles';
import type { TaxYear } from '../../types/taxes';
import { HelpOnHover } from '../HelpOnHover';
import type { InvestorTab } from '../InvestorTabs';
import {
  checkingOffCountsOf,
  daysLeftUntilTheYearEnds,
  monthNotSettledYet,
  taxPaidInSettledMonths,
  taxRefundedInSettledMonths,
} from './taxYears';

/** One cell of the year's card: its title, with its "?" 4px after it when it has one (`help`), then its figures. */
function YearCardCell({ title, help, children }: { title: string; help?: ReactNode; children: ReactNode }) {
  return (
    <div className="px-5 py-4">
      <p className="flex h-5 items-center gap-1 text-label text-muted">
        {title}
        {help}
      </p>
      <div className="mt-1">{children}</div>
    </div>
  );
}

/** "Paid ₪1,334.42 · Refunded ₪849.38", or "Nothing yet" before the first month's end. */
function settledText(taxYear: TaxYear): string {
  const paid = taxPaidInSettledMonths(taxYear);
  const refunded = taxRefundedInSettledMonths(taxYear);
  const parts = [];
  if (paid > 0) parts.push(`Paid ${formatShekels(paid)}`);
  if (refunded > 0) parts.push(`Refunded ${formatShekels(refunded)}`);
  return parts.length === 0 ? 'Nothing yet' : parts.join(' · ');
}

/**
 * What the current month will settle at its end — "Tax ₪34.98", "Refund ₪120.00" — or a shield waiting for a later
 * gain, "Shield carried ₪310.00"; "Nothing" otherwise, and once the year is over.
 */
function notSettledYetText(taxYear: TaxYear, isTheLastSyncsYear: boolean): string {
  if (!isTheLastSyncsYear) return 'Nothing';
  const currentMonth = monthNotSettledYet(taxYear);
  if (currentMonth !== null && currentMonth.settlementAmount > 0) {
    return `Tax ${formatShekels(currentMonth.settlementAmount)}`;
  }
  if (currentMonth !== null && currentMonth.settlementAmount < 0) {
    return `Refund ${formatShekels(-currentMonth.settlementAmount)}`;
  }
  return taxYear.shieldLeft > 0 ? `Shield carried ${formatShekels(taxYear.shieldLeft)}` : 'Nothing';
}

/** The "?" of the tax figure's title: how it is made, on Portfolio with the days confirmed as the broker recorded them. */
function taxOfTheYearExplanation(taxYear: TaxYear, tab: InvestorTab): string {
  const taxableGain = formatShekels(taxYear.taxableGain);
  return tab.investorId === null
    ? `25% of the year’s net taxable gain, ${taxableGain}, with each confirmed day as the broker recorded it.`
    : `25% of the year’s net taxable gain, ${taxableGain}, from the trades entered for ${tab.name}. In shekels: it doesn’t touch ${tab.name}’s cash.`;
}

/** Portfolio only: "3 of 5 days confirmed", with "1 to type in" under it, and a quiet ✓ once every day is. */
function CheckingOff({ taxYear }: { taxYear: TaxYear }) {
  const { confirmedDays, daysNeedingAConfirmation, daysToTypeIn } = checkingOffCountsOf(taxYear.settlementDays);
  if (daysNeedingAConfirmation === 0) return <p>Nothing to confirm</p>;
  const isEveryDayConfirmed = confirmedDays === daysNeedingAConfirmation;
  return (
    <>
      <p className="flex items-center gap-1.5">
        {isEveryDayConfirmed && <Check size={16} className="text-muted" />}
        {confirmedDays} of {daysNeedingAConfirmation} days confirmed
      </p>
      {daysToTypeIn > 0 && <p className="text-caption text-muted">{daysToTypeIn} to type in</p>}
    </>
  );
}

/**
 * The year's shield the broker won't refund (CAPITAL_GAINS_TAX_TODO.md, decision 5): on December 31 it refunds a shield
 * only up to the tax paid that year — and the monthly settlements have refunded that already. Information for a decision
 * (principle 2), never a warning.
 */
function YearEndShieldStrip({ shieldLeft, lastSyncDay }: { shieldLeft: number; lastSyncDay: string }) {
  return (
    <div className="flex items-center gap-2 rounded-b-lg border-t bg-accent-soft px-5 py-2.5 text-[13px]">
      <Info size={16} className="shrink-0 text-accent" />
      <span>
        <span className="font-semibold">{formatShekels(shieldLeft)}</span> of shield lost on Dec 31 ·{' '}
        <span className="font-semibold">{formatDayCount(daysLeftUntilTheYearEnds(lastSyncDay))} left</span>
      </span>
      <HelpOnHover label="Why the shield is lost">
        On Dec 31 the broker refunds a shield only up to the tax paid that year. Selling a stock in profit before then
        would use it.
      </HelpOnHover>
    </div>
  );
}

/**
 * The year in one card: its tax so far, what the broker paid and refunded at the months' ends, what the current month
 * will settle, and — on Portfolio — how far the checking off has come. The strip of a shield lost on December 31 comes
 * under it, only on the year of the last sync. On an investor's tab: three cells, as calculated from their own trades.
 */
export function TaxYearCard({
  taxYear,
  tab,
  lastSyncDay,
}: {
  taxYear: TaxYear;
  tab: InvestorTab;
  lastSyncDay: string;
}) {
  const isPortfolioTab = tab.investorId === null;
  const isTheLastSyncsYear = Number(lastSyncDay.slice(0, 4)) === taxYear.year;
  return (
    <section aria-label={`The tax of ${taxYear.year}`} className={CARD_CLASS}>
      <div className={`grid divide-x ${isPortfolioTab ? 'grid-cols-4' : 'grid-cols-3'}`}>
        <YearCardCell
          title={isTheLastSyncsYear ? 'Tax this year so far' : 'Tax of the year'}
          help={<HelpOnHover label="How the year’s tax is made">{taxOfTheYearExplanation(taxYear, tab)}</HelpOnHover>}
        >
          <p className="text-figure-lg tabular-nums">{formatShekels(taxYear.taxOfTheYearSoFar)}</p>
        </YearCardCell>
        <YearCardCell title="Settled">
          <p>{settledText(taxYear)}</p>
        </YearCardCell>
        <YearCardCell title="Not settled yet">
          <p>{notSettledYetText(taxYear, isTheLastSyncsYear)}</p>
        </YearCardCell>
        {isPortfolioTab && (
          <YearCardCell title="Checking off">
            <CheckingOff taxYear={taxYear} />
          </YearCardCell>
        )}
      </div>
      {isTheLastSyncsYear && taxYear.shieldLeft > 0 && (
        <YearEndShieldStrip shieldLeft={taxYear.shieldLeft} lastSyncDay={lastSyncDay} />
      )}
    </section>
  );
}
