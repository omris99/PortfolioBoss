import { formatSignedMoney, profitLossColorClass } from '../lib/format';

/**
 * "+242.89 USD": a realized P&L, green or red, with its currency small after it — the closed positions, their totals and
 * the investors' cards. `isLarge`: the totals' 26px figure, its currency at the body size.
 */
export function CurrencyFigure({ value, currency, isLarge = false }: { value: number; currency: string; isLarge?: boolean }) {
  return (
    <span className={`whitespace-nowrap ${profitLossColorClass(value)} ${isLarge ? 'text-figure-lg' : ''}`}>
      {formatSignedMoney(value)}
      <span className={`font-normal text-faint ${isLarge ? 'ml-1.5 text-body' : 'ml-1 text-[11px]'}`}>{currency}</span>
    </span>
  );
}
