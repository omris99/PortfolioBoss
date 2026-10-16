import { useState, type ReactNode } from 'react';
import { ChevronDown, ChevronRight, Pencil } from 'lucide-react';
import { useSaveAction } from '../hooks/useSaveAction';
import { addInvestor, renameInvestor } from '../lib/apiClient';
import {
  ACCOUNT_CURRENCY,
  currencyTotalsInOrder,
  formatMoney,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import { INPUT_CLASS, isBlank } from '../lib/formInput';
import { CARD_CLASS, ICON_BUTTON_CLASS, TAG_CLASS } from '../lib/styles';
import type { Investor } from '../types/portfolio';
import { CashMovementsList } from './CashMovements';
import { CurrencyFigure } from './CurrencyFigure';
import { Field } from './FormFields';
import { Modal } from './Modal';
import { WarningBand } from './WarnMark';

const NAME_MAX_LENGTH = 60;   // the investor.name column

/** What an investor is, said where one is added and while there is none yet. */
export const INVESTOR_EXPLANATION =
  "Someone else whose money is in this IB account. Their deposits and trades are entered for them; the account owner " +
  "gets the rest of IB's figures.";

// ── adding and renaming ─────────────────────────────────────────────────────────────────────────

/**
 * Adds an investor, or renames `investorBeingRenamed` — the account owner too. A name already taken comes back from the
 * API (409) and is shown under the field. `onSaved` reloads the portfolio, then the modal closes.
 */
export function InvestorModal({
  investorBeingRenamed,
  onSaved,
  onClose,
}: {
  investorBeingRenamed: Investor | null;
  onSaved: () => Promise<void>;
  onClose: () => void;
}) {
  const [name, setName] = useState(investorBeingRenamed?.name ?? '');
  const { isSaving, errorMessage, setErrorMessage, runSave } = useSaveAction();
  const isRenaming = investorBeingRenamed !== null;

  const handleSubmit = async () => {
    if (isBlank(name)) {
      setErrorMessage('Enter a name.');
      return;
    }
    const isSaved = await runSave(async () => {
      if (investorBeingRenamed === null) {
        await addInvestor(name.trim());
      } else {
        await renameInvestor(investorBeingRenamed.id, name.trim());
      }
      await onSaved();
    });
    if (isSaved) onClose();
  };

  return (
    <Modal
      title={isRenaming ? 'Rename investor' : 'Add investor'}
      subtitle={isRenaming ? undefined : 'Someone else whose money is in this IB account.'}
      size="narrow"
      errorMessage={errorMessage}
      isSaving={isSaving}
      saveLabel={isRenaming ? 'Rename' : 'Add investor'}
      onSubmit={() => void handleSubmit()}
      onClose={onClose}
    >
      <Field label="Name">
        <input
          type="text"
          maxLength={NAME_MAX_LENGTH}
          value={name}
          onChange={(event) => setName(event.target.value)}
          className={`${INPUT_CLASS} w-full`}
        />
      </Field>
    </Modal>
  );
}

// ── a card ──────────────────────────────────────────────────────────────────────────────────────

/** One line of the statement: the label on the left (with a hint on hover), the figure on the right. */
function StatementRow({
  label,
  hint,
  isStrong = false,
  colorClass = '',
  children,
}: {
  label: ReactNode;
  hint?: string;
  isStrong?: boolean;
  colorClass?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b py-2">
      <dt title={hint} className={isStrong ? 'font-semibold text-ink' : 'text-muted'}>
        {label}
      </dt>
      <dd className={`text-right ${isStrong ? 'text-base font-semibold' : 'font-medium'} ${colorClass}`}>{children}</dd>
    </div>
  );
}

/** "+500.00 USD" and "+950.00 HKD" one under the other; "+0.00 USD" before any closed position. */
function RealizedPnlByCurrency({ realizedPnlByCurrency }: { realizedPnlByCurrency: Record<string, number> }) {
  const currencyTotals = currencyTotalsInOrder(realizedPnlByCurrency);
  if (currencyTotals.length === 0) return <CurrencyFigure value={0} currency={ACCOUNT_CURRENCY} />;
  return (
    <span className="inline-flex flex-col items-end gap-0.5">
      {currencyTotals.map(([currency, total]) => (
        <CurrencyFigure key={currency} value={total} currency={currency} />
      ))}
    </span>
  );
}

/** Where a card's figures come from, said under the name. */
function sourceLineOf(investor: Investor): string {
  return investor.accountOwner
    ? "IB's figures, less the other investors'."
    : 'From the deposits and trades entered for them.';
}

/** Another investor's deposits: what they come to, and the list behind a disclosure. */
function CashMovementsSection({ investor, onDataChanged }: { investor: Investor; onDataChanged: () => Promise<void> }) {
  const [isOpen, setIsOpen] = useState(false);
  return (
    <div className="flex flex-col gap-2">
      <dl>
        <StatementRow
          label={
            <>
              Deposited <span className="text-faint">(deposits less withdrawals)</span>
            </>
          }
        >
          {formatMoney(investor.depositsMinusWithdrawals)}
        </StatementRow>
      </dl>
      <button
        type="button"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((wasOpen) => !wasOpen)}
        className="-ml-0.5 inline-flex h-7 items-center gap-1 self-start rounded-sm pl-0.5 pr-1.5 font-semibold text-ink hover:bg-subtle"
      >
        {isOpen ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
        Deposits ({investor.cashMovements.length})
      </button>
      {isOpen && <CashMovementsList investor={investor} onDataChanged={onDataChanged} />}
    </div>
  );
}

/**
 * One investor's part of the account as a short statement (INVESTORS_TODO.md, decision 10), in the same order on every
 * card so they line up side by side: cash, shares, total value, and the profit on the shares — unrealized, realized per
 * currency, and the total in USD. Another investor's card adds what they deposited, and their deposits.
 */
export function InvestorCard({ investor, onDataChanged }: { investor: Investor; onDataChanged: () => Promise<void> }) {
  const [isRenaming, setIsRenaming] = useState(false);

  return (
    <section aria-label={investor.name} className={`flex flex-col gap-3 px-6 py-5 ${CARD_CLASS}`}>
      <div className="flex items-center gap-2">
        <h2 className="text-figure font-semibold">{investor.name}</h2>
        {investor.accountOwner && <span className={TAG_CLASS}>account owner</span>}
        <button
          type="button"
          aria-label={`Rename ${investor.name}`}
          title="Rename"
          onClick={() => setIsRenaming(true)}
          className={ICON_BUTTON_CLASS}
        >
          <Pencil size={14} />
        </button>
      </div>
      <p className="-mt-1.5 text-[13px] text-muted">{sourceLineOf(investor)}</p>

      {investor.warnings.map((warning) => (
        <WarningBand key={`${warning.type}-${warning.message}`} level="real">
          {warning.message}
        </WarningBand>
      ))}

      <dl className="border-t">
        <StatementRow label="Cash">{formatMoney(investor.cash)}</StatementRow>
        <StatementRow label="Shares" hint="At IB's last price">
          {formatMoney(investor.sharesValue)}
        </StatementRow>
        <StatementRow label="Total value" hint="Cash and shares" isStrong>
          {formatMoney(investor.totalValue)}
        </StatementRow>
        <StatementRow
          label="Unrealized P&L"
          hint="What the shares are worth less what they cost, at average cost"
          colorClass={profitLossColorClass(investor.unrealizedPnl)}
        >
          {formatSignedMoney(investor.unrealizedPnl)}
          <span className={`font-normal ${profitLossColorClass(investor.unrealizedPnlPercent)}`}>
            {' '}
            ({formatSignedPercent(investor.unrealizedPnlPercent)})
          </span>
        </StatementRow>
        <StatementRow label="Realized P&L" hint="Of the shares sold, per currency">
          <RealizedPnlByCurrency realizedPnlByCurrency={investor.realizedPnlByCurrency} />
        </StatementRow>
        <StatementRow
          label="Total P&L"
          hint="Unrealized and realized, in USD"
          isStrong
          colorClass={profitLossColorClass(investor.totalPnl)}
        >
          {formatSignedMoney(investor.totalPnl)}
        </StatementRow>
      </dl>

      {!investor.accountOwner && <CashMovementsSection investor={investor} onDataChanged={onDataChanged} />}

      {isRenaming && (
        <InvestorModal investorBeingRenamed={investor} onSaved={onDataChanged} onClose={() => setIsRenaming(false)} />
      )}
    </section>
  );
}
