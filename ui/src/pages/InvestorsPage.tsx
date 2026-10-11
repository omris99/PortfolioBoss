import { useState } from 'react';
import { addInvestor } from '../lib/apiClient';
import type { Investor } from '../types/portfolio';
import { EmptyBox } from '../components/EmptyBox';
import { AddInvestorButton, AddInvestorForm, INVESTOR_EXPLANATION, InvestorCard } from '../components/InvestorCard';
import { hasSeveralInvestors } from '../components/InvestorsContext';
import { PageHeader } from '../components/PageHeader';

/**
 * The Investors page (/investors): a card for each investor whose money is in the IB account. While the account owner
 * is the only one there is nothing to divide, so the page says what an investor is and offers to add one.
 */
export function InvestorsPage({
  investors,
  onDataChanged,
}: {
  investors: Investor[];
  onDataChanged: () => Promise<void>;
}) {
  const [isAdding, setIsAdding] = useState(false);
  const showsCards = hasSeveralInvestors(investors);

  const handleAdd = async (name: string) => {
    await addInvestor(name);
    await onDataChanged();
    setIsAdding(false);
  };

  return (
    <>
      <PageHeader
        title="Investors"
        description={
          showsCards ? "Each investor's part of the account. Together they always add up to IB's figures." : undefined
        }
      >
        {!isAdding && <AddInvestorButton onClick={() => setIsAdding(true)} />}
      </PageHeader>

      <div className="flex flex-col gap-6">
        {isAdding && <AddInvestorForm onAdd={handleAdd} onCancel={() => setIsAdding(false)} />}

        {showsCards ? (
          <div className="grid gap-4 md:grid-cols-2">
            {investors.map((investor) => (
              <InvestorCard key={investor.id} investor={investor} onDataChanged={onDataChanged} />
            ))}
          </div>
        ) : (
          !isAdding && <EmptyBox message={`Only the account owner so far. An investor is ${lowercaseFirst(INVESTOR_EXPLANATION)}`} />
        )}
      </div>
    </>
  );
}

/** "Someone else…" → "someone else…", to follow "An investor is". */
function lowercaseFirst(sentence: string): string {
  return sentence.charAt(0).toLowerCase() + sentence.slice(1);
}
