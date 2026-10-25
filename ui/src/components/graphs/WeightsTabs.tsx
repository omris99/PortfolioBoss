import { useRef, type KeyboardEvent } from 'react';
import { TOGGLE_GROUP_CLASS } from '../../lib/styles';
import type { InvestmentsAndCash } from './investmentsAndCash';

/**
 * How one tab looks. The names are in sentence case, so they are lighter than the forms' BUY / SELL capitals; the
 * selected tab is white and bold.
 */
function classNamesForTab(isSelectedTab: boolean): string {
  const selectedOrNotClassNames = isSelectedTab
    ? 'bg-surface font-semibold text-ink shadow-[inset_0_0_0_1px_var(--color-border-strong)]'
    : 'font-medium text-muted hover:text-ink';
  return `min-w-[72px] rounded-sm px-3 text-[13px] ${selectedOrNotClassNames}`;
}

/** The position of the tab after this one; after the last tab comes the first. */
function positionOfNextTab(positionOfThisTab: number, numberOfTabs: number): number {
  const isLastTab = positionOfThisTab === numberOfTabs - 1;
  return isLastTab ? 0 : positionOfThisTab + 1;
}

/** The position of the tab before this one; before the first tab comes the last. */
function positionOfPreviousTab(positionOfThisTab: number, numberOfTabs: number): number {
  const isFirstTab = positionOfThisTab === 0;
  return isFirstTab ? numberOfTabs - 1 : positionOfThisTab - 1;
}

/**
 * Whose weights to show — Portfolio · Omri · Shay — as a segmented control like the forms' BUY or SELL. Shared by the
 * Overview's graphs and the drawer's Weight tab; nothing while Portfolio is the only tab.
 *
 * The keyboard works the usual way for tabs: the Tab key reaches only the selected tab, and the → and ← arrow keys
 * select the next or the previous tab and move the keyboard to it.
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
  const tabButtonsInOrder = useRef<(HTMLButtonElement | null)[]>([]);
  if (tabs.length === 1) return null;

  const selectTabAndMoveKeyboardToIt = (positionOfTab: number) => {
    onSelect(tabs[positionOfTab].investorId);
    tabButtonsInOrder.current[positionOfTab]?.focus();
  };

  const selectNeighbourTabWhenArrowKeyIsPressed = (
    event: KeyboardEvent<HTMLButtonElement>,
    positionOfThisTab: number,
  ) => {
    if (event.key === 'ArrowRight') {
      event.preventDefault();
      selectTabAndMoveKeyboardToIt(positionOfNextTab(positionOfThisTab, tabs.length));
    }
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      selectTabAndMoveKeyboardToIt(positionOfPreviousTab(positionOfThisTab, tabs.length));
    }
  };

  return (
    <span role="tablist" aria-label="Whose weights" className={TOGGLE_GROUP_CLASS}>
      {tabs.map((tab, positionOfThisTab) => {
        const isSelectedTab = tab.investorId === selectedInvestorId;
        return (
          <button
            key={tab.investorId ?? 'portfolio'}
            ref={(tabButton) => {
              tabButtonsInOrder.current[positionOfThisTab] = tabButton;
            }}
            type="button"
            role="tab"
            aria-selected={isSelectedTab}
            tabIndex={isSelectedTab ? 0 : -1}
            onClick={() => onSelect(tab.investorId)}
            onKeyDown={(event) => selectNeighbourTabWhenArrowKeyIsPressed(event, positionOfThisTab)}
            className={classNamesForTab(isSelectedTab)}
          >
            {tab.name}
          </button>
        );
      })}
    </span>
  );
}
