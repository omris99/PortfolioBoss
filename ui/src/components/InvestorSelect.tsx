import { INPUT_CLASS } from '../lib/formInput';
import { hasSeveralInvestors, useInvestors } from './InvestorsContext';

/**
 * The "Investor" field of the trade forms: whose trade it is. Nothing while the account owner is the only investor —
 * the form then sends the account owner without asking.
 */
export function InvestorSelect({
  investorId,
  onChange,
}: {
  investorId: number | null;
  onChange: (investorId: number) => void;
}) {
  const investors = useInvestors();
  if (!hasSeveralInvestors(investors)) return null;

  return (
    <label className="flex flex-col gap-1 text-[10px] uppercase tracking-wide text-slate-500">
      Investor
      <select
        value={investorId ?? ''}
        onChange={(event) => onChange(Number(event.target.value))}
        className={INPUT_CLASS}
      >
        {investors.map((investor) => (
          <option key={investor.id} value={investor.id}>
            {investor.name}
          </option>
        ))}
      </select>
    </label>
  );
}
