import type { TradeRequest } from '../types/portfolio';

/** A write the API refused or could not be reached for; `message` is meant to be shown to the user as it is. */
export class ApiError extends Error {}

const API_UNREACHABLE_MESSAGE = 'Could not reach the PortfolioBoss API. Is ./run.sh running?';

/** What to show for a failed write: the API's own reason when there is one. */
export function errorMessageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Saving failed unexpectedly.';
}

export function changeSector(holdingId: number, sector: string | null): Promise<void> {
  return sendJson('PUT', `/api/holdings/${holdingId}/sector`, { sector });
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

/**
 * Sends one write. The body goes as JSON — the only kind the write endpoints accept. The response body is not
 * read on success: after a write the caller reloads the whole portfolio.
 */
async function sendJson(method: 'PUT' | 'POST' | 'DELETE', url: string, body?: unknown): Promise<void> {
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
    throw new ApiError(await readErrorMessage(response));
  }
}

/**
 * The API answers errors with a ProblemDetail JSON body whose `detail` says what was wrong
 * ("quantity: must be greater than 0"). Anything else — say, Vite's own error page when the API is down —
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
