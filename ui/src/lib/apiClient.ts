import type {
  AnalysisRun,
  CashMovementRequest,
  ManualPositionRequest,
  NewManualPositionRequest,
  SyncRun,
  TradeRequest,
} from '../types/portfolio';
import type { AlertSetting } from '../types/alerts';
import type { RecommendationAnalysisRun, ScanRun } from '../types/recommendations';
import type { TaxConfirmationCorrectionRequest, TaxConfirmationsRequest } from '../types/taxes';

/**
 * A write the API refused or could not be reached for; `message` is meant to be shown to the user as it is. `status` is
 * the HTTP status of a refusal (409, 503…), `null` when the API could not be reached at all.
 */
export class ApiError extends Error {
  readonly status: number | null;

  constructor(message: string, status: number | null = null) {
    super(message);
    this.status = status;
  }
}

const API_UNREACHABLE_MESSAGE = 'Could not reach the PortfolioBoss API. Is ./run.sh running?';

/** What to show for a failed write: the API's own reason when there is one. */
export function errorMessageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Saving failed unexpectedly.';
}

export function addTrade(holdingId: number, trade: TradeRequest): Promise<void> {
  return sendJson('POST', `/api/holdings/${holdingId}/trades`, trade);
}

export function changeTrade(tradeId: number, trade: TradeRequest): Promise<void> {
  return sendJson('PUT', `/api/trades/${tradeId}`, trade);
}

export function deleteTrade(tradeId: number): Promise<void> {
  return sendJson('DELETE', `/api/trades/${tradeId}`);
}

export function addManualPosition(manualPosition: NewManualPositionRequest): Promise<void> {
  return sendJson('POST', '/api/manual-positions', manualPosition);
}

export function changeManualPosition(manualPositionId: number, details: ManualPositionRequest): Promise<void> {
  return sendJson('PUT', `/api/manual-positions/${manualPositionId}`, details);
}

/** Its trades go with it. */
export function deleteManualPosition(manualPositionId: number): Promise<void> {
  return sendJson('DELETE', `/api/manual-positions/${manualPositionId}`);
}

/** A manual position's trades are then corrected and deleted like any trade: `changeTrade`, `deleteTrade`. */
export function addManualPositionTrade(manualPositionId: number, trade: TradeRequest): Promise<void> {
  return sendJson('POST', `/api/manual-positions/${manualPositionId}/trades`, trade);
}

/** Never a second account owner: the API adds other investors only. A taken name is refused (409). */
export function addInvestor(name: string): Promise<void> {
  return sendJson('POST', '/api/investors', { name });
}

/** The account owner's name too. */
export function renameInvestor(investorId: number, name: string): Promise<void> {
  return sendJson('PUT', `/api/investors/${investorId}`, { name });
}

/** Never for the account owner, whose cash comes from IB (400). */
export function addCashMovement(investorId: number, cashMovement: CashMovementRequest): Promise<void> {
  return sendJson('POST', `/api/investors/${investorId}/cash-movements`, cashMovement);
}

export function changeCashMovement(movementId: number, cashMovement: CashMovementRequest): Promise<void> {
  return sendJson('PUT', `/api/cash-movements/${movementId}`, cashMovement);
}

export function deleteCashMovement(movementId: number): Promise<void> {
  return sendJson('DELETE', `/api/cash-movements/${movementId}`);
}

/**
 * Analyzes the analyst ratings and news of the given holdings, or of every open one (`null`): two web searches and one
 * Claude call a stock, paid — about $0.04 and 5 Tavily credits each. Answers once every stock is done, which takes tens of
 * seconds, with what the run did and cost; the analyses themselves come with the next reload of the portfolio.
 */
export async function analyzeHoldings(holdingIds: number[] | null): Promise<AnalysisRun> {
  const response = await sendRequest('POST', '/api/analysis', { holdingIds });
  return (await response.json()) as AnalysisRun;
}

/**
 * The Recommendations page's paid analysis — `null`: the short list (the first 10 hot stocks) and then the code's pick;
 * a candidate's id: that one stock, with no new recommendation. Four web searches, one Claude call and Finnhub's figures
 * a stock. Answers once every stock is done (tens of seconds for the short list); the analyses and the pick come with the
 * next reload of the lists. 503 when a key is missing — for the short list, Finnhub's too —, 404 for a stock no longer
 * in the latest scan's lists.
 */
export async function analyzeRecommendations(candidateId: number | null): Promise<RecommendationAnalysisRun> {
  const body = candidateId === null ? {} : { candidateId };
  const response = await sendRequest('POST', '/api/recommendations/analysis', body);
  return (await response.json()) as RecommendationAnalysisRun;
}

/**
 * Asks for a new scan for hot stocks on demand: TWS must be open. Free (IB only); answers in about ten seconds, once the
 * scan is stored. Refused with 503 when TWS can't be read or no scan answered, and with 409 while another reading runs.
 * The body is `{}`: JSON only, so no other site can start one.
 */
export async function scanAgain(): Promise<ScanRun> {
  const response = await sendRequest('POST', '/api/scans', {});
  return (await response.json()) as ScanRun;
}

/**
 * Sync now: reads TWS again — everything ./run.sh reads at startup: the portfolio, the scan when one is due, the
 * holdings' contract details and Dow Jones targets, the closes, and Finnhub for the holdings in dollars — and stores
 * it, without restarting the app. TWS must be open. Refused with 503 when TWS can't be read, and with 409 while another
 * reading runs. The body is `{}`: JSON only, so no other site can start one.
 */
export async function syncNow(): Promise<SyncRun> {
  const response = await sendRequest('POST', '/api/syncs', {});
  return (await response.json()) as SyncRun;
}

/** The bell was opened: every alert it shows is seen, and its number goes back to none. The body is `{}`: JSON only. */
export async function markAlertsAsSeen(): Promise<void> {
  await sendJson('POST', '/api/alerts/seen', {});
}

/**
 * The settings drawer's Save: every type's line — on or off, and its number. The server checks the alerts again with
 * them at once, from the stored data (no TWS needed). 400 with the line named when one is wrong.
 */
export async function saveAlertSettings(settings: AlertSetting[]): Promise<void> {
  const lines = settings.map(({ alertType, enabled, threshold }) => ({ alertType, enabled, threshold }));
  await sendJson('PUT', '/api/alerts/settings', { settings: lines });
}

/**
 * The Taxes page's Confirm, Confirm the whole month, and an amount typed for a day not confirmed yet: what the broker
 * recorded for each day, stored together or not at all. 409 when a day is confirmed already, 400 when the calculation
 * has no entry for one.
 */
export function confirmTaxDays(request: TaxConfirmationsRequest): Promise<void> {
  return sendJson('POST', '/api/taxes/confirmations', request);
}

/** A confirmed day's amount or note corrected; 404 when the confirmation is gone. */
export function correctTaxConfirmation(
  confirmationId: number,
  correction: TaxConfirmationCorrectionRequest,
): Promise<void> {
  return sendJson('PUT', `/api/taxes/confirmations/${confirmationId}`, correction);
}

/** "Remove the confirmation": the day waits again, with its calculated amount. */
export function cancelTaxConfirmation(confirmationId: number): Promise<void> {
  return sendJson('DELETE', `/api/taxes/confirmations/${confirmationId}`);
}

/** Sends one write whose answer is not needed: after a write the caller reloads the whole portfolio. */
async function sendJson(method: 'PUT' | 'POST' | 'DELETE', url: string, body?: unknown): Promise<void> {
  await sendRequest(method, url, body);
}

/** Sends one request, its body as JSON — the only kind the write endpoints accept — and answers the successful response. */
async function sendRequest(method: 'PUT' | 'POST' | 'DELETE', url: string, body?: unknown): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(url, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(API_UNREACHABLE_MESSAGE);
  }
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status);
  }
  return response;
}

/**
 * The API answers errors with a ProblemDetail JSON body whose `detail` says what was wrong
 * ("quantity: must be greater than 0"). Anything else — for example Vite's own error page when the API is down —
 * gets a message with just the status.
 */
async function readErrorMessage(response: Response): Promise<string> {
  try {
    const problemDetail = (await response.json()) as { detail?: unknown };
    if (typeof problemDetail.detail === 'string' && problemDetail.detail !== '') {
      return problemDetail.detail;
    }
  } catch {
    // Not JSON: fall through to the status.
  }
  return `The API answered HTTP ${response.status}`;
}
