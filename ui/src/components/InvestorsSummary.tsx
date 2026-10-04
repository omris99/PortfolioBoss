import { useState, type FormEvent, type ReactNode } from 'react';
import { Pencil, Plus, TriangleAlert } from 'lucide-react';
import { addInvestor, errorMessageOf, renameInvestor } from '../lib/apiClient';
import { formatMoney, formatSignedMoney, formatSignedPercent, profitLossColorClass } from '../lib/format';
import { INPUT_CLASS, isBlank } from '../lib/formInput';
import type { Investor, InvestorWarning } from '../types/portfolio';
import { CashMovementsPanel } from './CashMovementsPanel';
import { hasSeveralInvestors } from './InvestorsContext';

const NAME_MAX_LENGTH = 60;   // the investor.name column

/** The currency of every figure on a card but the realized P&L, which is listed with it first. */
const ACCOUNT_CURRENCY = 'USD';

const BUTTON_CLASS =
  'rounded-md border border-emerald-500/50 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20 disabled:opacity-50';

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
        <button type="submit" disabled={isSaving} className={BUTTON_CLASS}>
          {isSaving ? 'Saving…' : submitLabel}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={isSaving}
          className="rounded-md border border-slate-700 px-3 py-1 text-xs text-slate-300 transition-colors hover:text-slate-100 disabled:opacity-50"
        >
          Cancel
        </button>
      </div>
      {errorMessage && <p className="text-[11px] text-rose-400">{errorMessage}</p>}
    </form>
  );
}

/** The form for a new investor, with a line on what one is. */
function AddInvestorForm({ onAdd, onCancel }: { onAdd: (name: string) => Promise<void>; onCancel: () => void }) {
  return (
    <div className="flex flex-col gap-2 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">Add an investor</div>
      <p className="text-[11px] text-slate-500">
        Someone else whose money is in this IB account. Their deposits and trades are entered for them; the account
        owner gets the rest of IB's figures.
      </p>
      <InvestorNameForm initialName="" submitLabel="Add investor" onSubmit={onAdd} onCancel={onCancel} />
    </div>
  );
}

function AddInvestorButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className={`flex items-center gap-1 ${BUTTON_CLASS}`}>
      <Plus size={12} />
      Add investor
    </button>
  );
}

// ── a card ──────────────────────────────────────────────────────────────────────────────────────

/** One line of a card: the label on the left (with a hint on hover), the figure on the right. */
function FigureRow({
  label,
  hint,
  colorClass = 'text-slate-100',
  children,
}: {
  label: string;
  hint?: string;
  colorClass?: string;
  children: ReactNode;
}) {
  return (
    <>
      <dt title={hint} className="text-slate-500">
        {label}
      </dt>
      <dd className={`text-right font-mono ${colorClass}`}>{children}</dd>
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
      <span className="text-slate-400">
        {formatSignedMoney(0)} <span className="text-[10px] text-slate-500">{ACCOUNT_CURRENCY}</span>
      </span>
    );
  }
  return (
    <span className="flex flex-col items-end">
      {currencyTotals.map(([currency, total]) => (
        <span key={currency} className={profitLossColorClass(total)}>
          {formatSignedMoney(total)} <span className="text-[10px] text-slate-500">{currency}</span>
        </span>
      ))}
    </span>
  );
}

/** Written out on the card, in the same orange as a holding's warnings. They never block anything. */
function InvestorWarningList({ warnings }: { warnings: InvestorWarning[] }) {
  if (warnings.length === 0) return null;
  return (
    <ul className="flex flex-col gap-1 text-[11px] text-orange-500">
      {warnings.map((warning) => (
        <li key={`${warning.type}-${warning.message}`} className="flex items-start gap-1.5">
          <TriangleAlert size={12} className="mt-0.5 shrink-0" />
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
      <span className="text-sm font-semibold text-slate-100">{investor.name}</span>
      {investor.accountOwner && (
        <span className="rounded bg-slate-800 px-1 py-0.5 text-[9px] font-medium uppercase text-slate-400">
          account owner
        </span>
      )}
      <button
        type="button"
        title="Rename"
        onClick={() => setIsRenaming(true)}
        className="rounded p-1 text-slate-500 transition-colors hover:bg-slate-800 hover:text-slate-100"
      >
        <Pencil size={12} />
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
function InvestorCard({ investor, onDataChanged }: { investor: Investor; onDataChanged: () => Promise<void> }) {
  const [showsCashMovements, setShowsCashMovements] = useState(false);

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-slate-700/80 bg-slate-900/60 p-4">
      <div>
        <InvestorCardHeader investor={investor} onDataChanged={onDataChanged} />
        <p className="mt-1 text-[11px] text-slate-500">{sourceLineOf(investor)}</p>
      </div>

      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-xs">
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
          <span className="text-[10px]">({formatSignedPercent(investor.unrealizedPnlPercent)})</span>
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
            className="self-start rounded-md border border-slate-700 px-3 py-1 text-xs text-slate-300 transition-colors hover:text-slate-100"
          >
            {showsCashMovements ? 'Hide deposits' : `Deposits (${investor.cashMovements.length})`}
          </button>
          {showsCashMovements && <CashMovementsPanel investor={investor} onDataChanged={onDataChanged} />}
        </div>
      )}
    </div>
  );
}

// ── the section ─────────────────────────────────────────────────────────────────────────────────

/**
 * A card for each investor whose money is in the IB account, under the account summary. While the account owner is
 * the only one there is nothing to divide, so only a small "+ Add investor" shows.
 */
export function InvestorsSummary({
  investors,
  onDataChanged,
}: {
  investors: Investor[];
  onDataChanged: () => Promise<void>;
}) {
  const [isAdding, setIsAdding] = useState(false);

  const handleAdd = async (name: string) => {
    await addInvestor(name);
    await onDataChanged();
    setIsAdding(false);
  };
  const addInvestorForm = <AddInvestorForm onAdd={handleAdd} onCancel={() => setIsAdding(false)} />;

  if (!hasSeveralInvestors(investors)) {
    return isAdding ? (
      addInvestorForm
    ) : (
      <div className="flex justify-end">
        <button
          type="button"
          onClick={() => setIsAdding(true)}
          className="text-xs text-slate-500 transition-colors hover:text-emerald-300"
        >
          + Add investor
        </button>
      </div>
    );
  }

  return (
    <section className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-sm font-semibold text-slate-100">Investors</div>
          <div className="mt-1 text-xs text-slate-400">
            Each investor's part of the account. Together they always add up to IB's figures.
          </div>
        </div>
        {!isAdding && <AddInvestorButton onClick={() => setIsAdding(true)} />}
      </div>

      {isAdding && <div className="mt-4">{addInvestorForm}</div>}

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        {investors.map((investor) => (
          <InvestorCard key={investor.id} investor={investor} onDataChanged={onDataChanged} />
        ))}
      </div>
    </section>
  );
}
