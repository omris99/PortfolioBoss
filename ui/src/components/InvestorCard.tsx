import { useState, type FormEvent, type ReactNode } from 'react';
import { Pencil, Plus, TriangleAlert } from 'lucide-react';
import { errorMessageOf, renameInvestor } from '../lib/apiClient';
import { formatMoney, formatSignedMoney, formatSignedPercent, profitLossColorClass } from '../lib/format';
import { INPUT_CLASS, isBlank } from '../lib/formInput';
import {
  CARD_CLASS,
  ERROR_TEXT_CLASS,
  HINT_TEXT_CLASS,
  ICON_BUTTON_CLASS,
  PANEL_TITLE_CLASS,
  PRIMARY_BUTTON_CLASS,
  SECONDARY_BUTTON_CLASS,
  TAG_CLASS,
} from '../lib/styles';
import type { Investor, InvestorWarning } from '../types/portfolio';
import { CashMovementsPanel } from './CashMovementsPanel';

const NAME_MAX_LENGTH = 60;   // the investor.name column

/** The currency of every figure on a card but the realized P&L, which is listed with it first. */
const ACCOUNT_CURRENCY = 'USD';

// ── adding and renaming ─────────────────────────────────────────────────────────────────────────

/**
 * One name field: adding an investor, or renaming one — the account owner too. `onSubmit` saves and reloads; a name
 * already taken comes back from the API (409) and is shown under the field.
 */
function InvestorNameForm({
  initialName,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initialName: string;
  submitLabel: string;
  onSubmit: (name: string) => Promise<void>;
  onCancel: () => void;
}) {
  const [name, setName] = useState(initialName);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (isBlank(name)) {
      setErrorMessage('Enter a name.');
      return;
    }
    setIsSaving(true);
    setErrorMessage(null);
    try {
      await onSubmit(name.trim());
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <form onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center gap-2">
        <input
          autoFocus
          type="text"
          required
          maxLength={NAME_MAX_LENGTH}
          placeholder="Name"
          aria-label="Investor name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          className={`${INPUT_CLASS} w-40`}
        />
        <button type="submit" disabled={isSaving} className={PRIMARY_BUTTON_CLASS}>
          {isSaving ? 'Saving…' : submitLabel}
        </button>
        <button type="button" onClick={onCancel} disabled={isSaving} className={SECONDARY_BUTTON_CLASS}>
          Cancel
        </button>
      </div>
      {errorMessage && <p className={ERROR_TEXT_CLASS}>{errorMessage}</p>}
    </form>
  );
}

/** What an investor is, said where one is added and while there is none yet. */
export const INVESTOR_EXPLANATION =
  "Someone else whose money is in this IB account. Their deposits and trades are entered for them; the account owner " +
  "gets the rest of IB's figures.";

/** The form for a new investor, with a line on what one is. */
export function AddInvestorForm({ onAdd, onCancel }: { onAdd: (name: string) => Promise<void>; onCancel: () => void }) {
  return (
    <div className={`flex flex-col gap-3 p-5 ${CARD_CLASS}`}>
      <div className={PANEL_TITLE_CLASS}>Add an investor</div>
      <p className={HINT_TEXT_CLASS}>{INVESTOR_EXPLANATION}</p>
      <InvestorNameForm initialName="" submitLabel="Add investor" onSubmit={onAdd} onCancel={onCancel} />
    </div>
  );
}

export function AddInvestorButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className={PRIMARY_BUTTON_CLASS}>
      <Plus size={16} />
      Add investor
    </button>
  );
}

// ── a card ──────────────────────────────────────────────────────────────────────────────────────

/** One line of a card: the label on the left (with a hint on hover), the figure on the right. */
function FigureRow({
  label,
  hint,
  colorClass = 'text-ink',
  children,
}: {
  label: string;
  hint?: string;
  colorClass?: string;
  children: ReactNode;
}) {
  return (
    <>
      <dt title={hint} className="text-muted">
        {label}
      </dt>
      <dd className={`text-right ${colorClass}`}>{children}</dd>
    </>
  );
}

/** USD first, then the other currencies A→Z. */
function compareAccountCurrencyFirst(firstCurrency: string, secondCurrency: string): number {
  if (firstCurrency === ACCOUNT_CURRENCY) return -1;
  if (secondCurrency === ACCOUNT_CURRENCY) return 1;
  return firstCurrency.localeCompare(secondCurrency);
}

/** "+500.00 USD" and "+950.00 HKD" one under the other; "+0.00 USD" before any closed position. */
function RealizedPnlByCurrency({ realizedPnlByCurrency }: { realizedPnlByCurrency: Record<string, number> }) {
  const currencyTotals = Object.entries(realizedPnlByCurrency).sort(([firstCurrency], [secondCurrency]) =>
    compareAccountCurrencyFirst(firstCurrency, secondCurrency),
  );
  if (currencyTotals.length === 0) {
    return (
      <span>
        {formatSignedMoney(0)} <span className="text-caption text-muted">{ACCOUNT_CURRENCY}</span>
      </span>
    );
  }
  return (
    <span className="flex flex-col items-end">
      {currencyTotals.map(([currency, total]) => (
        <span key={currency} className={profitLossColorClass(total)}>
          {formatSignedMoney(total)} <span className="text-caption text-muted">{currency}</span>
        </span>
      ))}
    </span>
  );
}

/** Written out on the card, in the same orange as a holding's warnings. They never block anything. */
function InvestorWarningList({ warnings }: { warnings: InvestorWarning[] }) {
  if (warnings.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1 text-caption text-warn-real">
      {warnings.map((warning) => (
        <li key={`${warning.type}-${warning.message}`} className="flex items-start gap-2">
          <TriangleAlert size={14} className="shrink-0" />
          {warning.message}
        </li>
      ))}
    </ul>
  );
}

/** The name, an "account owner" tag, and a ✏️ that turns the name into a field. */
function InvestorCardHeader({ investor, onDataChanged }: { investor: Investor; onDataChanged: () => Promise<void> }) {
  const [isRenaming, setIsRenaming] = useState(false);

  const handleRename = async (name: string) => {
    await renameInvestor(investor.id, name);
    await onDataChanged();
    setIsRenaming(false);
  };

  if (isRenaming) {
    return (
      <InvestorNameForm
        initialName={investor.name}
        submitLabel="Save"
        onSubmit={handleRename}
        onCancel={() => setIsRenaming(false)}
      />
    );
  }
  return (
    <div className="flex items-center gap-2">
      <span className="text-section-title">{investor.name}</span>
      {investor.accountOwner && <span className={TAG_CLASS}>account owner</span>}
      <button type="button" title="Rename" onClick={() => setIsRenaming(true)} className={ICON_BUTTON_CLASS}>
        <Pencil size={14} />
      </button>
    </div>
  );
}

/** Where a card's figures come from, said under the name. */
function sourceLineOf(investor: Investor): string {
  return investor.accountOwner
    ? "IB's figures, less the other investors'."
    : 'From the deposits and trades entered for them.';
}

/**
 * One investor's part of the account (INVESTORS_TODO.md, decision 10): cash, shares, total value, and the profit on
 * the shares — unrealized, realized per currency, and the total in USD. Another investor's card also shows what they
 * deposited, and opens their deposits and withdrawals.
 */
export function InvestorCard({ investor, onDataChanged }: { investor: Investor; onDataChanged: () => Promise<void> }) {
  const [showsCashMovements, setShowsCashMovements] = useState(false);

  return (
    <div className={`flex flex-col gap-4 p-5 ${CARD_CLASS}`}>
      <div>
        <InvestorCardHeader investor={investor} onDataChanged={onDataChanged} />
        <p className="mt-1 text-caption text-muted">{sourceLineOf(investor)}</p>
      </div>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5">
        {!investor.accountOwner && (
          <FigureRow label="Deposited" hint="Deposits less withdrawals">
            {formatMoney(investor.depositsMinusWithdrawals)}
          </FigureRow>
        )}
        <FigureRow label="Cash">{formatMoney(investor.cash)}</FigureRow>
        <FigureRow label="Shares" hint="At IB's last price">
          {formatMoney(investor.sharesValue)}
        </FigureRow>
        <FigureRow label="Total value" hint="Cash and shares">
          {formatMoney(investor.totalValue)}
        </FigureRow>
        <FigureRow
          label="Unrealized P&L"
          hint="What the shares are worth less what they cost, at average cost"
          colorClass={profitLossColorClass(investor.unrealizedPnl)}
        >
          {formatSignedMoney(investor.unrealizedPnl)}{' '}
          <span className="text-caption">({formatSignedPercent(investor.unrealizedPnlPercent)})</span>
        </FigureRow>
        <FigureRow label="Realized P&L" hint="Of the shares sold, per currency">
          <RealizedPnlByCurrency realizedPnlByCurrency={investor.realizedPnlByCurrency} />
        </FigureRow>
        <FigureRow
          label="Total P&L"
          hint="Unrealized and realized, in USD"
          colorClass={profitLossColorClass(investor.totalPnl)}
        >
          {formatSignedMoney(investor.totalPnl)}
        </FigureRow>
      </dl>

      <InvestorWarningList warnings={investor.warnings} />

      {!investor.accountOwner && (
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={() => setShowsCashMovements((isShown) => !isShown)}
            className={`self-start ${SECONDARY_BUTTON_CLASS}`}
          >
            {showsCashMovements ? 'Hide deposits' : `Deposits (${investor.cashMovements.length})`}
          </button>
          {showsCashMovements && <CashMovementsPanel investor={investor} onDataChanged={onDataChanged} />}
        </div>
      )}
    </div>
  );
}
