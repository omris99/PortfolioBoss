import { Download, FileText } from 'lucide-react';
import { CARD_CLASS } from '../../lib/styles';

/**
 * Stands in for the Taxes page until IB's trade history is in. Two states, and no button: the history downloads by
 * itself with every sync that reaches TWS (CAPITAL_GAINS_TAX_TODO.md, 1.4).
 * - `isFlexConnected` false: the keys of IB's Flex report are missing.
 * - true: the keys are there, and nothing is downloaded yet.
 */
export function TaxSetupCard({ isFlexConnected }: { isFlexConnected: boolean }) {
  return (
    <section className={`${CARD_CLASS} flex items-start gap-4 px-5 py-4`}>
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-accent-soft text-accent">
        {isFlexConnected ? <Download size={20} /> : <FileText size={20} />}
      </span>
      {isFlexConnected ? (
        <div>
          <h2 className="text-section-title">IB’s trade history is not downloaded yet</h2>
          <p className="mt-1 text-muted">
            IB’s trade history downloads with the next sync: press Sync now in the top bar, with TWS open.
          </p>
        </div>
      ) : (
        <div>
          <h2 className="text-section-title">IB’s report is not connected</h2>
          <p className="mt-1 text-muted">
            To work out the tax, PortfolioBoss needs IB’s Flex report: add <code className="text-code">FLEX_TOKEN</code>{' '}
            and <code className="text-code">FLEX_QUERY_ID</code> to <code className="text-code">config/local.env</code>.
          </p>
        </div>
      )}
    </section>
  );
}
