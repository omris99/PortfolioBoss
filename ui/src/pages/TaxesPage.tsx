import { useState } from 'react';
import { EmptyBox } from '../components/EmptyBox';
import { FormError, SelectInput } from '../components/FormFields';
import { HelpOnHover } from '../components/HelpOnHover';
import { InvestorTabs } from '../components/InvestorTabs';
import { PageHeader } from '../components/PageHeader';
import { SettlementDaysTable } from '../components/taxes/SettlementDaysTable';
import { TaxSetupCard } from '../components/taxes/TaxSetupCard';
import { TaxYearCard } from '../components/taxes/TaxYearCard';
import { tabsOf, taxYearsOfTab, yearOfTheLastSync, yearsToChooseFrom } from '../components/taxes/taxYears';
import { useSaveAction } from '../hooks/useSaveAction';
import { useTaxes } from '../hooks/useTaxes';
import { confirmTaxDays } from '../lib/apiClient';
import type { SettlementDay } from '../types/taxes';

/** The set with the key added, or without it when it was there. */
function withKeyToggled(keys: Set<string>, key: string): Set<string> {
  const toggledKeys = new Set(keys);
  if (toggledKeys.has(key)) {
    toggledKeys.delete(key);
  } else {
    toggledKeys.add(key);
  }
  return toggledKeys;
}

/** What stands in for the page while there is nothing to show: the load, no sync yet, or the API not answering. */
function messageWithoutTaxes(isLoading: boolean, hasNoSyncYet: boolean, errorMessage: string | null): string {
  if (errorMessage !== null) return errorMessage;
  if (hasNoSyncYet) return 'Nothing synced yet: press Sync now in the top bar, with TWS open.';
  return isLoading ? 'Loading the taxes…' : 'No data.';
}

const PAGE_DESCRIPTION = (
  <span className="inline-flex items-center gap-1">
    Tax on every sale, as the broker withholds it.
    <HelpOnHover label="How to check the days off">
      Check each day off against the broker’s monthly statement: Confirm takes the amount PortfolioBoss calculated as the
      one the broker recorded; the pencil is for the amount the broker recorded when it is another.
    </HelpOnHover>
  </span>
);

/**
 * The Taxes page (`/taxes`, CAPITAL_GAINS_TAX_TODO.md session 5, from Claude Design's fifth round): the Israeli capital
 * gains tax of every sale in the account, as the broker withholds it, to check off against its monthly statements. A year
 * to choose (the last sync's first) and a tab for Portfolio and each investor besides the account owner; the year's card
 * and its settlement days by month. Only Portfolio's days are checked off — what the broker recorded is the one thing the
 * page saves; an investor's tab is read only. Until IB's report is connected and downloaded, a card says what is missing.
 */
export function TaxesPage({ portfolioSyncedAt }: { portfolioSyncedAt: string }) {
  const { taxes, hasNoSyncYet, errorMessage, isLoading, reload } = useTaxes(portfolioSyncedAt);
  // `null`: the year of the last sync.
  const [selectedYear, setSelectedYear] = useState<number | null>(null);
  // `null`: Portfolio.
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | null>(null);
  const [openedSettlementDays, setOpenedSettlementDays] = useState<Set<string>>(new Set());
  const [openedStockSalesByDay, setOpenedStockSalesByDay] = useState<Map<string, Set<string>>>(new Map());
  const { isSaving: isConfirming, errorMessage: confirmationErrorMessage, runSave } = useSaveAction();

  const closeEverythingOpened = () => {
    setOpenedSettlementDays(new Set());
    setOpenedStockSalesByDay(new Map());
  };

  const selectTab = (investorId: number | null) => {
    setSelectedInvestorId(investorId);
    closeEverythingOpened();
  };

  const selectYear = (year: number) => {
    setSelectedYear(year);
    closeEverythingOpened();
  };

  const toggleSettlementDay = (settlementDay: string) => {
    setOpenedSettlementDays((openedDays) => withKeyToggled(openedDays, settlementDay));
  };

  const toggleStockSales = (settlementDay: string, symbol: string) => {
    setOpenedStockSalesByDay((openedByDay) => {
      const symbolsOpenedOnTheDay = openedByDay.get(settlementDay) ?? new Set<string>();
      return new Map(openedByDay).set(settlementDay, withKeyToggled(symbolsOpenedOnTheDay, symbol));
    });
  };

  /** Confirm and Confirm the whole month: the days at the amounts PortfolioBoss calculated, then the page again. */
  const confirmAsCalculated = (settlementDays: SettlementDay[]) => {
    const confirmations = settlementDays.map((settlementDay) => ({
      settlementDay: settlementDay.settlementDay,
      taxAmount: settlementDay.computedTaxAmount,
      note: null,
    }));
    void runSave(async () => {
      await confirmTaxDays({ confirmations });
      await reload();
    });
  };

  const header = <PageHeader title="Taxes" description={PAGE_DESCRIPTION} />;
  if (taxes === null) {
    return (
      <>
        {header}
        <EmptyBox message={messageWithoutTaxes(isLoading, hasNoSyncYet, errorMessage)} />
      </>
    );
  }
  if (!taxes.flexConnected || taxes.flexReportsStoredUpTo === null) {
    return (
      <>
        {header}
        <TaxSetupCard isFlexConnected={taxes.flexConnected} />
      </>
    );
  }

  const shownYear = selectedYear ?? yearOfTheLastSync(taxes);
  const tabs = tabsOf(taxes);
  const selectedTab = tabs.find((tab) => tab.investorId === selectedInvestorId) ?? tabs[0];
  const isPortfolioTab = selectedTab.investorId === null;
  const taxYear = taxYearsOfTab(taxes, selectedTab.investorId).find((year) => year.year === shownYear) ?? null;

  return (
    <>
      {header}
      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-4">
          <span className="w-28">
            <SelectInput
              aria-label="Tax year"
              value={shownYear}
              onChange={(event) => selectYear(Number(event.target.value))}
            >
              {yearsToChooseFrom(taxes).map((year) => (
                <option key={year} value={year}>
                  {year}
                </option>
              ))}
            </SelectInput>
          </span>
          <InvestorTabs
            tabs={tabs}
            selectedInvestorId={selectedTab.investorId}
            onSelect={selectTab}
            ariaLabel="Whose tax"
          />
        </div>
        {confirmationErrorMessage !== null && <FormError>{confirmationErrorMessage}</FormError>}
        {taxYear === null ? (
          <EmptyBox message={`No sales in ${shownYear}.`} />
        ) : (
          <>
            <TaxYearCard taxYear={taxYear} tab={selectedTab} lastSyncDay={taxes.lastSyncDay} />
            <SettlementDaysTable
              taxYear={taxYear}
              isPortfolioTab={isPortfolioTab}
              openedSettlementDays={openedSettlementDays}
              openedStockSalesByDay={openedStockSalesByDay}
              isConfirming={isConfirming}
              onToggleSettlementDay={toggleSettlementDay}
              onToggleStockSales={toggleStockSales}
              onConfirmAsCalculated={confirmAsCalculated}
              onConfirmationChanged={reload}
            />
          </>
        )}
        <p className="mt-2 flex items-center gap-1 text-caption text-faint">
          IB’s report up to {taxes.flexReportsStoredUpTo}
          <HelpOnHover label="Where the figures come from">
            Rates: the Bank of Israel’s representative rate of each settlement day. Nothing is paid or sent: the broker
            withholds the tax itself.
          </HelpOnHover>
        </p>
      </div>
    </>
  );
}
