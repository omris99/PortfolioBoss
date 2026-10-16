import { SelectInput } from './FormFields';
import { useInvestors } from './InvestorsContext';

/**
 * Whose trade it is, in a form; its form shows it only with several investors — with the account owner alone the form
 * sends the account owner without asking.
 */
export function InvestorSelect({
  investorId,
  onChange,
}: {
  investorId: number | null;
  onChange: (investorId: number) => void;
}) {
  const investors = useInvestors();
  return (
    <SelectInput value={investorId ?? ''} onChange={(event) => onChange(Number(event.target.value))}>
      {investors.map((investor) => (
        <option key={investor.id} value={investor.id}>
          {investor.name}
        </option>
      ))}
    </SelectInput>
  );
}
