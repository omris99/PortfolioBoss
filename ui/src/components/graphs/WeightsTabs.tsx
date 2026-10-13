import { TOGGLE_GROUP_CLASS, toggleOptionClass } from '../../lib/styles';
import type { InvestmentsAndCash } from './investmentsAndCash';

/**
 * Whose weights to show — Portfolio · Omri · Shay — as the segmented control of the forms' BUY or SELL. Shared by the
 * Overview's graphs and the drawer's Weight tab; nothing while Portfolio is the only tab.
 */
export function WeightsTabs({
  tabs,
  selectedInvestorId,
  onSelect,
}: {
  tabs: InvestmentsAndCash[];
  /** `null`: Portfolio. */
  selectedInvestorId: number | null;
  onSelect: (investorId: number | null) => void;
}) {
  if (tabs.length === 1) return null;
  return (
    <span role="tablist" aria-label="Whose weights" className={TOGGLE_GROUP_CLASS}>
      {tabs.map((tab) => {
        const isSelected = tab.investorId === selectedInvestorId;
        return (
          <button
            key={tab.investorId ?? 'portfolio'}
            type="button"
            role="tab"
            aria-selected={isSelected}
            onClick={() => onSelect(tab.investorId)}
            className={toggleOptionClass(isSelected)}
          >
            {tab.name}
          </button>
        );
      })}
    </span>
  );
}
