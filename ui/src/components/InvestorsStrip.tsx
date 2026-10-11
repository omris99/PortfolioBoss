import { ArrowRight, ChevronRight } from 'lucide-react';
import { Link } from 'react-router';
import { formatMoney, formatSignedMoney, profitLossColorClass } from '../lib/format';
import { CARD_CLASS, TAG_CLASS } from '../lib/styles';
import type { Investor } from '../types/portfolio';
import { hasSeveralInvestors } from './InvestorsContext';

function StripFigure({ label, value, colorClass = 'text-ink' }: { label: string; value: string; colorClass?: string }) {
  return (
    <span className="text-right">
      <span className="block text-label text-muted">{label}</span>
      <span className={`mt-0.5 block text-figure ${colorClass}`}>{value}</span>
    </span>
  );
}

/**
 * On the Overview, under the rest: each investor's total value and total P&L, linking to the Investors page. Nothing
 * while the account owner is the only investor.
 */
export function InvestorsStrip({ investors }: { investors: Investor[] }) {
  if (!hasSeveralInvestors(investors)) return null;

  return (
    <section aria-label="Investors">
      <div className="mb-3 flex items-center gap-2">
        <h2 className="text-section-title">Investors</h2>
        <Link
          to="/investors"
          className="ml-auto inline-flex items-center gap-1 text-caption font-medium text-accent underline-offset-2 hover:underline"
        >
          Investors page
          <ArrowRight size={14} />
        </Link>
      </div>
      <div className="grid grid-cols-[repeat(auto-fit,minmax(360px,1fr))] gap-4">
        {investors.map((investor) => (
          <Link
            key={investor.id}
            to="/investors"
            className={`group grid grid-cols-[1fr_auto_auto_16px] items-center gap-x-8 px-5 py-4 transition-colors hover:border-strong ${CARD_CLASS}`}
          >
            <span className="flex min-w-0 items-center gap-2 font-semibold">
              {investor.name}
              {investor.accountOwner && <span className={TAG_CLASS}>account owner</span>}
            </span>
            <StripFigure label="Total value" value={formatMoney(investor.totalValue)} />
            <StripFigure
              label="Total P&L"
              value={formatSignedMoney(investor.totalPnl)}
              colorClass={profitLossColorClass(investor.totalPnl)}
            />
            <ChevronRight size={16} className="text-faint group-hover:text-ink" />
          </Link>
        ))}
      </div>
    </section>
  );
}
