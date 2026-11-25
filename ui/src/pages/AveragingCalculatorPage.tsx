import { useState, type ReactNode } from 'react';
import { Info, Wallet } from 'lucide-react';
import { useLocation } from 'react-router';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { investorIdToOpenOn } from '../components/averagingCalculator/AveragingCalculatorLink';
import { AveragingCashCard } from '../components/averagingCalculator/AveragingCashCard';
import {
  calculatePlanTotal,
  calculateRowResult,
  NOTHING_TYPED,
  splitHoldingsByAverage,
  type AveragingRow,
  type TypedTexts,
} from '../components/averagingCalculator/averagingPlan';
import { AveragingSection } from '../components/averagingCalculator/AveragingSection';
import { AveragingTable, type AveragingTableEntry } from '../components/averagingCalculator/AveragingTable';
import { NotOnThisPage } from '../components/averagingCalculator/NotOnThisPage';
import { weightsTabOf, weightsTabsOf, type InvestmentsAndCash } from '../components/graphs/investmentsAndCash';
import { wholeAccountOf, type WholeAccount } from '../components/graphs/portfolioWeights';
import { InvestorTabs } from '../components/InvestorTabs';
import { PageHeader } from '../components/PageHeader';
import { formatDateTime, formatMoney } from '../lib/format';
import { openHoldingsOf } from '../lib/holdings';
import { CARD_CLASS } from '../lib/styles';
import type { PortfolioSnapshot } from '../types/portfolio';

/** What is typed on one tab, by the holding's id. */
type TypedTextsByHoldingId = Record<number, TypedTexts>;

const BELOW_AVERAGE_LEAD =
  'If you held none of it, would you buy it today at this price? Under each holding is what PortfolioBoss knows.';

/** The key of each tab's own plan: "portfolio", or "investor-3". */
function planKeyOf(tab: InvestmentsAndCash): string {
  return tab.investorId === null ? 'portfolio' : `investor-${tab.investorId}`;
}

function noneBelowAverageText(tab: InvestmentsAndCash): string {
  const holdingsName = tab.investorId === null ? 'the holdings' : `${tab.name}’s holdings`;
  return `None of ${holdingsName} is below its average cost.`;
}

/** "Dana holds no stocks · cash 7,065.00". */
function noStocksText(tab: InvestmentsAndCash): string {
  const holderName = tab.investorId === null ? 'The account' : tab.name;
  return `${holderName} holds no stocks · cash ${formatMoney(tab.cash)}`;
}

/** One line in a card, in place of the cash card and the sections. */
function PageLine({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <div className={`${CARD_CLASS} flex items-center gap-2 px-5 py-4`}>
      {icon}
      {children}
    </div>
  );
}

/** The holding's row as the table shows it: with what is typed on it on this tab, and what that comes to. */
function buildTableEntry(averagingRow: AveragingRow, typedTextsOfTab: TypedTextsByHoldingId): AveragingTableEntry {
  const typedTexts = typedTextsOfTab[averagingRow.holding.id] ?? NOTHING_TYPED;
  return { averagingRow, typedTexts, rowResult: calculateRowResult(averagingRow, typedTexts) };
}

/**
 * One tab's plan: the cash card with every purchase typed on the tab added up, the holdings below their average cost,
 * those at or above it — folded until opened —, and the holdings the page leaves out.
 */
function TabPlan({
  tab,
  wholeAccount,
  typedTextsOfTab,
  isAtOrAboveAverageOpen,
  onTypedTextsChange,
  onClear,
  onAtOrAboveAverageToggle,
}: {
  tab: InvestmentsAndCash;
  wholeAccount: WholeAccount;
  typedTextsOfTab: TypedTextsByHoldingId;
  isAtOrAboveAverageOpen: boolean;
  onTypedTextsChange: (holdingId: number, typedTexts: TypedTexts) => void;
  onClear: () => void;
  onAtOrAboveAverageToggle: () => void;
}) {
  const { belowAverage, atOrAboveAverage, leftOut } = splitHoldingsByAverage(tab);
  const belowAverageEntries = belowAverage.map((averagingRow) => buildTableEntry(averagingRow, typedTextsOfTab));
  const atOrAboveAverageEntries = atOrAboveAverage.map((averagingRow) => buildTableEntry(averagingRow, typedTextsOfTab));
  const everyRowResult = [...belowAverageEntries, ...atOrAboveAverageEntries].map((entry) => entry.rowResult);
  const planTotal = calculatePlanTotal(everyRowResult, wholeAccount.cash);
  const plannedAtOrAboveAverageCount = atOrAboveAverageEntries.filter(
    (entry) => entry.rowResult.kind === 'purchase',
  ).length;

  return (
    <>
      <AveragingCashCard tab={tab} cash={wholeAccount.cash} planTotal={planTotal} onClear={onClear} />
      <AveragingSection
        title="Below the average cost"
        count={belowAverageEntries.length}
        lead={BELOW_AVERAGE_LEAD}
        emptyText={noneBelowAverageText(tab)}
      >
        <AveragingTable entries={belowAverageEntries} onTypedTextsChange={onTypedTextsChange} />
      </AveragingSection>
      {atOrAboveAverageEntries.length > 0 && (
        <AveragingSection
          title="At or above the average cost"
          count={atOrAboveAverageEntries.length}
          folding={{
            isOpen: isAtOrAboveAverageOpen,
            plannedPurchaseCount: plannedAtOrAboveAverageCount,
            onToggle: onAtOrAboveAverageToggle,
          }}
        >
          <AveragingTable entries={atOrAboveAverageEntries} onTypedTextsChange={onTypedTextsChange} />
        </AveragingSection>
      )}
      <NotOnThisPage leftOutHoldings={leftOut} />
    </>
  );
}

/**
 * The Averaging calculator (/averaging; MY_HOLDINGS_TODO.md, decision 13): buying more of several holdings at once —
 * those whose price fell below their average cost first —, each with the Weight tab's purchase (a target weight or an
 * amount, the new average and how far the price must rise to reach it), and every purchase added up against the cash.
 * The tabs are the weights': Portfolio, then each investor, and each tab is a plan of its own, kept while the page is
 * open. It opens on Portfolio, or on the tab a link from the drawer's Weight tab asked for. A calculation only: nothing
 * reaches IB and nothing is saved.
 */
export function AveragingCalculatorPage({ snapshot }: { snapshot: PortfolioSnapshot }) {
  const location = useLocation();
  useDocumentTitle('Averaging calculator');
  const tabs = weightsTabsOf(openHoldingsOf(snapshot.holdings), snapshot.totalCashValue, snapshot.investors);
  const [selectedInvestorId, setSelectedInvestorId] = useState<number | null>(() => investorIdToOpenOn(location.state));
  const [typedTextsByPlanKey, setTypedTextsByPlanKey] = useState<Record<string, TypedTextsByHoldingId>>({});
  const [isAtOrAboveAverageOpen, setIsAtOrAboveAverageOpen] = useState(false);

  const tab = weightsTabOf(tabs, selectedInvestorId);
  const planKey = planKeyOf(tab);
  const wholeAccount = wholeAccountOf(tab);

  const changeTypedTexts = (holdingId: number, typedTexts: TypedTexts) => {
    setTypedTextsByPlanKey((previousTypedTexts) => ({
      ...previousTypedTexts,
      [planKey]: { ...previousTypedTexts[planKey], [holdingId]: typedTexts },
    }));
  };

  const clearPlanOfTab = () => {
    setTypedTextsByPlanKey((previousTypedTexts) => ({ ...previousTypedTexts, [planKey]: {} }));
  };

  const renderTabBody = (): ReactNode => {
    if (tab.investments.length === 0) {
      return <PageLine icon={<Wallet size={16} className="shrink-0 text-muted" />}>{noStocksText(tab)}</PageLine>;
    }
    if (wholeAccount === null) {
      return (
        <PageLine icon={<Info size={16} className="shrink-0 text-muted" />}>
          No weight can be worked out: {tab.wholeName} is unknown (IB reported no cash) or not above zero.
        </PageLine>
      );
    }
    return (
      <TabPlan
        tab={tab}
        wholeAccount={wholeAccount}
        typedTextsOfTab={typedTextsByPlanKey[planKey] ?? {}}
        isAtOrAboveAverageOpen={isAtOrAboveAverageOpen}
        onTypedTextsChange={changeTypedTexts}
        onClear={clearPlanOfTab}
        onAtOrAboveAverageToggle={() => setIsAtOrAboveAverageOpen((isOpen) => !isOpen)}
      />
    );
  };

  return (
    <>
      <PageHeader
        title="Averaging calculator"
        description="Plan buying more of several holdings at once: each one’s new average, and what is left of the cash."
      />
      <div className="flex flex-col gap-4">
        {tabs.length > 1 && (
          <div className="flex items-center gap-3 text-caption">
            <InvestorTabs
              tabs={tabs}
              selectedInvestorId={tab.investorId}
              onSelect={setSelectedInvestorId}
              ariaLabel="Whose weights"
            />
            <span className="text-faint">Weights of {tab.wholeName}. Each tab is a plan of its own.</span>
          </div>
        )}
        {renderTabBody()}
        <p className="mt-2 text-caption text-faint">
          At IB’s last prices, from the sync of {formatDateTime(snapshot.asOf)}. A calculation only: nothing is sold or
          bought.
        </p>
      </div>
    </>
  );
}
