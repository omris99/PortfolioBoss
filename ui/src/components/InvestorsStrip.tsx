import type { ReactNode } from 'react';
import { ArrowRight, ChevronRight } from 'lucide-react';
import { Link } from 'react-router';
import { formatMoney, formatSignedMoney, profitLossColorClass } from '../lib/format';
import { CARD_CLASS, TAG_CLASS } from '../lib/styles';
import type { Investor } from '../types/portfolio';
import { SkeletonBar } from './firstLoad/SkeletonBar';
import { hasSeveralInvestors } from './InvestorsContext';

const SECTION_TITLE = 'Investors';
/** The investors' cards side by side, as many as fit. */
const CARDS_GRID_CLASS = 'grid grid-cols-[repeat(auto-fit,minmax(360px,1fr))] gap-4';
/** A card's columns — the name, the two figures, the arrow —, the same while they load. */
const CARD_COLUMNS_CLASS = `grid grid-cols-[1fr_auto_auto_16px] items-center gap-x-8 px-5 py-4 ${CARD_CLASS}`;
const TOTAL_VALUE_LABEL = 'Total value';
const TOTAL_PNL_LABEL = 'Total P&L';

function StripFigure({
  label,
  value,
  colorClass = 'text-ink',
}: {
  label: string;
  value: ReactNode;
  colorClass?: string;
}) {
  return (
    <span className="text-right">
      <span className="block text-label text-muted">{label}</span>
      <span className={`mt-0.5 block text-figure ${colorClass}`}>{value}</span>
    </span>
  );
}

/** The section's title and the link to the Investors page. */
function InvestorsHeading() {
  return (
    <div className="mb-3 flex items-center gap-2">
      <h2 className="text-section-title">{SECTION_TITLE}</h2>
      <Link
        to="/investors"
        className="ml-auto inline-flex items-center gap-1 text-caption font-medium text-accent underline-offset-2 hover:underline"
      >
        Investors page
        <ArrowRight size={14} />
      </Link>
    </div>
  );
}

/**
 * On the Overview, under the rest: each investor's total value and total P&L, linking to the Investors page. Nothing
 * while the account owner is the only investor.
 */
export function InvestorsStrip({ investors }: { investors: Investor[] }) {
  if (!hasSeveralInvestors(investors)) return null;

  return (
    <section aria-label={SECTION_TITLE}>
      <InvestorsHeading />
      <div className={CARDS_GRID_CLASS}>
        {investors.map((investor) => (
          <Link
            key={investor.id}
            to="/investors"
            className={`group ${CARD_COLUMNS_CLASS} transition-colors hover:border-strong`}
          >
            <span className="flex min-w-0 items-center gap-2 font-semibold">
              {investor.name}
              {investor.accountOwner && <span className={TAG_CLASS}>account owner</span>}
            </span>
            <StripFigure label={TOTAL_VALUE_LABEL} value={formatMoney(investor.totalValue)} />
            <StripFigure
              label={TOTAL_PNL_LABEL}
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

/** How wide each of the two grey names is. */
const SKELETON_NAME_WIDTHS = [140, 64];

/**
 * The same section during the first load: its title and link, and two cards with their labels and grey bars. Whether the
 * account has several investors is known only with the data: with one, the section goes when the data comes.
 */
export function InvestorsStripSkeleton() {
  return (
    <section aria-label={SECTION_TITLE} aria-busy="true">
      <InvestorsHeading />
      <div className={CARDS_GRID_CLASS}>
        {SKELETON_NAME_WIDTHS.map((nameWidth) => (
          <div key={nameWidth} className={CARD_COLUMNS_CLASS}>
            <span>
              <SkeletonBar width={nameWidth} height={12} />
            </span>
            <StripFigure label={TOTAL_VALUE_LABEL} value={<SkeletonBar width={110} height={20} />} />
            <StripFigure label={TOTAL_PNL_LABEL} value={<SkeletonBar width={96} height={20} />} />
            <span />
          </div>
        ))}
      </div>
    </section>
  );
}
