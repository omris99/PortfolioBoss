import { ArrowRight } from 'lucide-react';
import { Link } from 'react-router';
import { LINK_BUTTON_CLASS } from '../../lib/styles';

/** What the link tells the Averaging calculator: whose tab to open on — `null` for Portfolio. */
interface AveragingCalculatorLinkState {
  investorId: number | null;
}

/** The investor whose tab a link asked the Averaging calculator to open on; `null` for Portfolio, or with no such link. */
export function investorIdToOpenOn(state: unknown): number | null {
  if (typeof state !== 'object' || state === null) return null;
  const { investorId } = state as Partial<AveragingCalculatorLinkState>;
  return typeof investorId === 'number' ? investorId : null;
}

/**
 * In the drawer's Weight tab, whatever is typed: the way to plan buying more of several holdings at once, on the same
 * tab — Dana's tab leads to Dana's plan.
 */
export function AveragingCalculatorLink({ investorId }: { investorId: number | null }) {
  const state: AveragingCalculatorLinkState = { investorId };
  return (
    <p className="mt-3 text-caption text-muted">
      Buying more of several holdings?{' '}
      <Link to="/averaging" state={state} className={LINK_BUTTON_CLASS}>
        Averaging calculator
        <ArrowRight size={12} />
      </Link>
    </p>
  );
}
