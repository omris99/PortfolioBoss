import { useState } from 'react';
import { Plus } from 'lucide-react';
import { CARD_CLASS, PRIMARY_BUTTON_CLASS, SECONDARY_BUTTON_CLASS } from '../lib/styles';
import type { Investor } from '../types/portfolio';
import { INVESTOR_EXPLANATION, InvestorCard, InvestorModal } from '../components/InvestorCard';
import { hasSeveralInvestors } from '../components/InvestorsContext';
import { PageHeader } from '../components/PageHeader';

/** "Someone else…" → "someone else…", to follow "An investor is". */
function lowercaseFirst(sentence: string): string {
  return sentence.charAt(0).toLowerCase() + sentence.slice(1);
}

/** While the account owner is the only investor: what investors are for, and a way to add one. */
function OnlyTheAccountOwner({ onAdd }: { onAdd: () => void }) {
  return (
    <section className={`max-w-[640px] p-6 ${CARD_CLASS}`}>
      <h2 className="text-section-title">Only the account owner, for now</h2>
      <p className="mb-4 mt-2">An investor is {lowercaseFirst(INVESTOR_EXPLANATION)}</p>
      <p className="mb-4 text-muted">
        Together the investors always add up to IB’s figures. With one investor, every page shows IB’s figures as they
        are.
      </p>
      <button type="button" onClick={onAdd} className={PRIMARY_BUTTON_CLASS}>
        <Plus size={16} />
        Add investor
      </button>
    </section>
  );
}

/**
 * The Investors page (/investors): a card for each investor whose money is in the IB account, two to a row, the account
 * owner first — or, while the account owner is alone, what an investor is and a way to add one.
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

  return (
    <>
      <PageHeader
        title="Investors"
        description={showsCards ? 'Together they always add up to IB’s figures.' : undefined}
      >
        {showsCards && (
          <button type="button" onClick={() => setIsAdding(true)} className={SECONDARY_BUTTON_CLASS}>
            <Plus size={16} />
            Add investor
          </button>
        )}
      </PageHeader>

      {showsCards ? (
        <div className="grid grid-cols-[repeat(auto-fit,minmax(420px,1fr))] items-start gap-6">
          {investors.map((investor) => (
            <InvestorCard key={investor.id} investor={investor} onDataChanged={onDataChanged} />
          ))}
        </div>
      ) : (
        <OnlyTheAccountOwner onAdd={() => setIsAdding(true)} />
      )}

      {isAdding && (
        <InvestorModal investorBeingRenamed={null} onSaved={onDataChanged} onClose={() => setIsAdding(false)} />
      )}
    </>
  );
}
