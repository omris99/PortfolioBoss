import { Fragment, type ReactNode } from 'react';
import { Check, Info, LoaderCircle, RefreshCw, TriangleAlert, Wallet } from 'lucide-react';
import type { ScanAgainState } from '../../hooks/useScanAgain';
import { formatDateTime, formatMoney } from '../../lib/format';
import { CARD_CLASS, EXPLAINED_CLASS, SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { Recommendations } from '../../types/recommendations';
import { hasSeveralInvestors, useInvestors } from '../InvestorsContext';

const NO_LIST_HINT = 'No long upward trend, or fewer than 200 trading days: in no list.';

function Separator() {
  return (
    <span aria-hidden="true" className="px-1.5 text-faint">
      ·
    </span>
  );
}

/** "Scanned 2026-10-23 23:05 · 68 stocks · 34 hot, 19 too hot, 8 correcting, 7 in no list". */
function ScanFigures({ recommendations }: { recommendations: Recommendations }) {
  const inListsCount =
    recommendations.hotStocks.length + recommendations.tooHotStocks.length + recommendations.correctingStocks.length;
  const inNoListCount = recommendations.candidateCount - inListsCount;
  return (
    <p>
      <b className="font-semibold">Scanned {formatDateTime(recommendations.scannedAt)}</b>
      <Separator />
      {recommendations.candidateCount} stocks
      <Separator />
      {recommendations.hotStocks.length} hot, {recommendations.tooHotStocks.length} too hot,{' '}
      {recommendations.correctingStocks.length} correcting,{' '}
      <span title={NO_LIST_HINT} className={EXPLAINED_CLASS}>
        {inNoListCount} in no list
      </span>
    </p>
  );
}

/** Scan again's status, to the left of the button. */
function ScanAgainStatus({ scanAgainState }: { scanAgainState: ScanAgainState }) {
  let status: ReactNode = null;
  if (scanAgainState.kind === 'scanning') {
    status = <span className="text-muted">Scanning… about 10 seconds</span>;
  } else if (scanAgainState.kind === 'done') {
    status = (
      <>
        <Check size={14} className="text-positive" aria-hidden="true" />
        New scan loaded
      </>
    );
  } else if (scanAgainState.kind === 'busy') {
    status = (
      <>
        <Info size={14} className="text-muted" aria-hidden="true" />
        {scanAgainState.message}
      </>
    );
  } else if (scanAgainState.kind === 'failed') {
    status = (
      <>
        <TriangleAlert size={14} className="text-negative" aria-hidden="true" />
        {scanAgainState.message}
      </>
    );
  }
  return (
    <span role="status" className="inline-flex items-center gap-1.5 text-[13px]">
      {status}
    </span>
  );
}

/** The free scan of IB, on demand (TWS must be open); disabled while it runs. */
function ScanAgainButton({ scanAgainState, onScanAgain }: { scanAgainState: ScanAgainState; onScanAgain: () => void }) {
  const isScanning = scanAgainState.kind === 'scanning';
  return (
    <button type="button" disabled={isScanning} onClick={onScanAgain} className={SECONDARY_BUTTON_CLASS}>
      {isScanning ? (
        <LoaderCircle size={16} className="animate-spin motion-reduce:animate-none" aria-hidden="true" />
      ) : (
        <RefreshCw size={16} aria-hidden="true" />
      )}
      {isScanning ? 'Scanning…' : 'Scan again'}
    </button>
  );
}

/**
 * The scan line (DESIGN_BRIEF.md §8.2): when the scan ran and what it found, with Scan again on the right — or, before
 * the first scan, what will bring one.
 */
function ScanLine({
  recommendations,
  scanAgainState,
  onScanAgain,
}: {
  recommendations: Recommendations | null;
  scanAgainState: ScanAgainState;
  onScanAgain: () => void;
}) {
  return (
    <div className="flex items-center gap-4 px-5 py-4">
      <div className="min-w-0">
        {recommendations === null ? (
          <p>
            No scan yet. <span className="font-mono text-code">./run.sh</span> scans once a trading day, after the US
            market closes — or press Scan again (TWS must be open).
          </p>
        ) : (
          <>
            <ScanFigures recommendations={recommendations} />
            <p className="mt-0.5 text-caption text-faint">
              A new scan comes once a trading day, after the US market closes.
            </p>
          </>
        )}
      </div>
      <div className="ml-auto flex shrink-0 items-center gap-3">
        <ScanAgainStatus scanAgainState={scanAgainState} />
        <ScanAgainButton scanAgainState={scanAgainState} onScanAgain={onScanAgain} />
      </div>
    </div>
  );
}

/** Each investor's free cash, the account owner first; with one investor, just "Cash". */
function FreeCash() {
  const investors = useInvestors();
  const namedCashes = hasSeveralInvestors(investors)
    ? investors.map((investor) => ({ key: investor.id, name: investor.name, cash: investor.cash }))
    : investors.map((investor) => ({ key: investor.id, name: 'Cash', cash: investor.cash }));
  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-t bg-subtle px-5 py-3">
      <span className="inline-flex items-center gap-1.5 text-caption font-semibold text-muted">
        <Wallet size={14} aria-hidden="true" />
        Free cash
      </span>
      {namedCashes.map((namedCash, index) => (
        <Fragment key={namedCash.key}>
          {index > 0 && <span aria-hidden="true" className="text-faint">·</span>}
          <span className="inline-flex items-baseline gap-1.5">
            <span className="text-muted">{namedCash.name}</span>
            <span className="text-[15px] font-semibold">{formatMoney(namedCash.cash)}</span>
          </span>
        </Fragment>
      ))}
      <span className="ml-auto text-caption text-faint">What a new investment would be paid from</span>
    </div>
  );
}

/**
 * The card at the top of the page: the scan line, then the free cash under it — in the same card, so that when the
 * recommendation arrives above the lists, cash and picks are read together and nothing here moves.
 */
export function ScanCard({
  recommendations,
  scanAgainState,
  onScanAgain,
}: {
  recommendations: Recommendations | null;
  scanAgainState: ScanAgainState;
  onScanAgain: () => void;
}) {
  return (
    <section aria-label="Scan" className={`overflow-hidden ${CARD_CLASS}`}>
      <ScanLine recommendations={recommendations} scanAgainState={scanAgainState} onScanAgain={onScanAgain} />
      <FreeCash />
    </section>
  );
}
