import { useLocation, useNavigate, useParams } from 'react-router';
import { EmptyBox } from '../components/EmptyBox';
import { weightsTabsOf } from '../components/graphs/investmentsAndCash';
import { HelpPopover } from '../components/HelpPopover';
import { PageHeader } from '../components/PageHeader';
import { AnalysisDetailLine } from '../components/recommendations/AnalysisDetailLine';
import { RowAnalyzeButton } from '../components/recommendations/AnalyzeButtons';
import { CandidateDrawer } from '../components/recommendations/CandidateDrawer';
import { CORRECTING_COLUMNS, HOT_COLUMNS, TOO_HOT_COLUMNS } from '../components/recommendations/hotStockColumns';
import { RecommendationCard } from '../components/recommendations/RecommendationCard';
import { ScanCard } from '../components/recommendations/ScanCard';
import { StockListCard } from '../components/recommendations/StockListCard';
import { useRecommendationAnalysis } from '../hooks/useRecommendationAnalysis';
import { useRecommendations } from '../hooks/useRecommendations';
import { useScanAgain } from '../hooks/useScanAgain';
import { idFromAddress, OPENED_FROM_TABLE, wasOpenedFromTable } from '../lib/drawerAddress';
import { openHoldingsOf } from '../lib/holdings';
import type { PortfolioSnapshot } from '../types/portfolio';
import type { HotStock, Recommendations } from '../types/recommendations';

/** Every stock of the three lists, in the page's order: Hot, Too hot, Correcting — the drawer's ‹ › follow it. */
function stocksInPageOrder(recommendations: Recommendations | null): HotStock[] {
  if (recommendations === null) return [];
  return [...recommendations.hotStocks, ...recommendations.tooHotStocks, ...recommendations.correctingStocks];
}

/** The stock `step` away from `candidateId` in the page's order, round the ends. */
function neighbourOf(stocks: HotStock[], candidateId: number, step: number): HotStock {
  const index = stocks.findIndex((stock) => stock.candidateId === candidateId);
  return stocks[(index + step + stocks.length) % stocks.length];
}

/** The Hot list's "?": what makes a stock hot, and the list's order (RECOMMENDATIONS_TODO.md, decision 1). */
function HotListHelp() {
  return (
    <HelpPopover label="What makes a stock hot">
      <p>
        A long upward trend: the price above its 200-day average, and the 50-day average above the 200-day one. Hot is
        then 0–15% above the 50-day average.
      </p>
      <p className="mt-2">
        The order: pulled back first, then the higher momentum score, then the less stretched above the 50-day average.
      </p>
    </HelpPopover>
  );
}

/** The Hot list's line under an analyzed row; none under the others. */
function detailLineOf(stock: HotStock) {
  return stock.analysis === null ? null : <AnalysisDetailLine stock={stock} />;
}

/**
 * The Recommendations page (/recommendations, RECOMMENDATIONS_TODO.md sessions 1b and 4): the latest scan's US stocks
 * in a long upward trend, in three lists, the code's latest recommendation above them, and at /recommendations/40 that
 * stock's drawer over them. The open stock lives in the address, like a holding's. Nothing is bought: Scan again reads
 * IB, and the paid Analyze buttons read the web, Claude and Finnhub — one run at a time on the page. `snapshot` brings
 * the analysis's cost estimate and, for If I buy, the holdings and the cash.
 */
export function RecommendationsPage({ snapshot }: { snapshot: PortfolioSnapshot }) {
  const navigate = useNavigate();
  const location = useLocation();
  const openCandidateId = idFromAddress(useParams().candidateId);
  const { recommendations, hasNoScanYet, errorMessage, isLoading, reload } = useRecommendations();
  const { scanAgainState, runScanAgain } = useScanAgain(reload);
  const { analysisState, runAnalysis } = useRecommendationAnalysis(reload);
  const estimate = snapshot.estimatedAnalysisCost;
  const openHoldings = openHoldingsOf(snapshot.holdings);
  const analyze = (candidateId: number | null) => void runAnalysis(candidateId);
  const analyzeButtonOf = (stock: HotStock) => (
    <RowAnalyzeButton stock={stock} estimate={estimate} analysisState={analysisState} onAnalyze={analyze} />
  );

  const allStocks = stocksInPageOrder(recommendations);
  const openStock = allStocks.find((stock) => stock.candidateId === openCandidateId) ?? null;

  const openDrawer = (candidateId: number) =>
    navigate(`/recommendations/${candidateId}`, { state: OPENED_FROM_TABLE });
  const closeDrawer = () => {
    if (wasOpenedFromTable(location.state)) {
      navigate(-1);
    } else {
      navigate('/recommendations', { replace: true });
    }
  };
  // ‹ › replace the address rather than adding a step per stock to the browser's history.
  const stepTo = (stock: HotStock) =>
    navigate(`/recommendations/${stock.candidateId}`, { replace: true, state: location.state });

  const isFirstLoad = recommendations === null && !hasNoScanYet;
  return (
    <>
      <PageHeader
        title="Recommendations"
        description={<span className="text-faint">US stocks in a long upward trend, from IB’s scan</span>}
      />
      {isFirstLoad ? (
        <EmptyBox message={isLoading ? 'Loading the scan…' : (errorMessage ?? 'No data.')} />
      ) : (
        <div className="flex flex-col gap-5">
          <ScanCard
            recommendations={recommendations}
            scanAgainState={scanAgainState}
            onScanAgain={() => void runScanAgain()}
          />
          {recommendations !== null && (
            <>
              <RecommendationCard
                recommendations={recommendations}
                estimate={estimate}
                analysisState={analysisState}
                onAnalyze={analyze}
                onOpen={openDrawer}
              />
              <StockListCard
                list="HOT"
                stocks={recommendations.hotStocks}
                columns={HOT_COLUMNS}
                hasFixedWidths={false}
                openCandidateId={openCandidateId}
                onOpen={openDrawer}
                analyzeButtonOf={analyzeButtonOf}
                detailLineOf={detailLineOf}
                headingAside={<HotListHelp />}
              />
              <StockListCard
                list="TOO_HOT"
                stocks={recommendations.tooHotStocks}
                columns={TOO_HOT_COLUMNS}
                hasFixedWidths
                openCandidateId={openCandidateId}
                onOpen={openDrawer}
                analyzeButtonOf={analyzeButtonOf}
              />
              <StockListCard
                list="CORRECTING"
                stocks={recommendations.correctingStocks}
                columns={CORRECTING_COLUMNS}
                hasFixedWidths
                openCandidateId={openCandidateId}
                onOpen={openDrawer}
                analyzeButtonOf={analyzeButtonOf}
              />
            </>
          )}
        </div>
      )}
      {openStock !== null && (
        <CandidateDrawer
          key={openStock.candidateId}
          stock={openStock}
          previousSymbol={neighbourOf(allStocks, openStock.candidateId, -1).symbol}
          nextSymbol={neighbourOf(allStocks, openStock.candidateId, 1).symbol}
          onPrevious={() => stepTo(neighbourOf(allStocks, openStock.candidateId, -1))}
          onNext={() => stepTo(neighbourOf(allStocks, openStock.candidateId, 1))}
          onClose={closeDrawer}
          estimate={estimate}
          analysisState={analysisState}
          onAnalyze={analyze}
          weightsTabs={weightsTabsOf(openHoldings, snapshot.totalCashValue, snapshot.investors)}
          openHoldings={openHoldings}
        />
      )}
    </>
  );
}
