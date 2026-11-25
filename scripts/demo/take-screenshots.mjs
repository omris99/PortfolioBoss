// Screenshots of every screen of PortfolioBoss on the demo database (1440 × 900 at 2×), for a design round or to check a
// change; the app must be running on the demo database (create-demo-db.sh, then ./run.sh 7599 — see
// implementation-plans/UI_POLISH_TODO.md).
//   cd scripts/demo && npm install          # once: playwright-core, which drives the Chrome already installed
//   node take-screenshots.mjs <output folder>
//   ONLY=drawer node take-screenshots.mjs <output folder>    # only the screenshots whose name holds "drawer"
// Refuses to run unless the API serves the demo account. Every request that writes is blocked in the browser — no paid
// analysis, no scan, no save can start — except Sync now, which fails on port 7599 (its error banner is a screen too).
import { chromium } from 'playwright-core';
import { mkdirSync } from 'node:fs';

const OUTPUT = process.argv[2];
const UI = 'http://localhost:5174';
const API = 'http://localhost:8080/api';
const DEMO_ACCOUNT = 'U1234567';

mkdirSync(OUTPUT, { recursive: true });
const portfolio = await (await fetch(`${API}/portfolio`)).json();
if (portfolio.account !== DEMO_ACCOUNT) {
  throw new Error(`the API serves ${portfolio.account}, not the demo account: no screenshot taken`);
}
const recommendations = await (await fetch(`${API}/recommendations`)).json();
const holdingIdOf = (symbol) => portfolio.holdings.find((holding) => holding.symbol === symbol).id;

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 2,
  locale: 'en-US',
  timezoneId: 'Asia/Jerusalem',
});
await context.route('**/api/**', (route) => {
  const request = route.request();
  const isSyncNow = request.method() === 'POST' && request.url().endsWith('/api/syncs');
  return request.method() === 'GET' || isSyncNow ? route.continue() : route.abort();
});
const page = await context.newPage();

let shotNumber = 0;
async function shot(name, prepare, { fullPage = false } = {}) {
  shotNumber += 1;
  if (process.env.ONLY && !name.includes(process.env.ONLY)) return;
  const file = `${OUTPUT}/${String(shotNumber).padStart(2, '0')}-${name}.png`;
  try {
    await prepare();
    await page.waitForTimeout(700);
    await page.screenshot({ path: file, fullPage });
    console.log(`ok     ${file}`);
  } catch (error) {
    console.log(`FAILED ${name}: ${error.message.split('\n')[0]}`);
  }
}

async function open(path) {
  await page.goto(`${UI}${path}`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(400);
}

const clickTab = (name) => page.getByRole('tab', { name, exact: false }).first().click();

// The pages
await shot('overview', () => open('/'), { fullPage: true });
await shot('overview-first-screen', () => open('/'));
await shot('positions', () => open('/positions'), { fullPage: true });
await shot('positions-columns-menu', async () => {
  await open('/positions');
  await page.getByRole('button', { name: 'Columns' }).click();
});
await shot('positions-analyze-confirm', async () => {
  await open('/positions');
  await page.getByRole('button', { name: /^Analyze/ }).first().click();
  await page.getByLabel('Confirm the analysis').waitFor();
});
await shot('averaging-calculator', () => open('/averaging'), { fullPage: true });
await shot('averaging-calculator-with-a-purchase', async () => {
  await open('/averaging');
  await page.locator('input').first().fill('10');
}, { fullPage: true });
await shot('closed-positions', () => open('/closed'), { fullPage: true });
await shot('investors', () => open('/investors'), { fullPage: true });
await shot('recommendations', () => open('/recommendations'), { fullPage: true });
await shot('taxes-2026', () => open('/taxes'), { fullPage: true });

// A holding's drawer, tab by tab: NVDA (analyzed), TEAM (shared with Dana), WMT (never analyzed)
const nvda = `/positions/${holdingIdOf('NVDA')}`;
await shot('holding-drawer-summary', () => open(nvda));
for (const [tab, name] of [['Momentum', 'momentum'], ['Analysts', 'analysts-and-news'], ['Company', 'company'],
  ['Trades', 'trades'], ['Weight', 'weight']]) {
  await shot(`holding-drawer-${name}`, async () => {
    await open(nvda);
    await clickTab(tab);
  });
}
await shot('holding-drawer-by-investor', async () => {
  await open(`/positions/${holdingIdOf('TEAM')}`);
  await clickTab('By investor');
});
await shot('holding-drawer-never-analyzed', async () => {
  await open(`/positions/${holdingIdOf('WMT')}`);
  await clickTab('Analysts');
});
await shot('trade-modal', async () => {
  await open(nvda);
  await clickTab('Trades');
  await page.getByRole('button', { name: 'Add trade' }).click();
});

// A closed position's drawer, and the manual one
await shot('closed-drawer', async () => {
  await open('/closed');
  await page.locator('tbody tr').first().click();
});
await shot('closed-drawer-manual-position', async () => {
  await open('/closed');
  await page.locator('tbody tr', { hasText: 'MSFT' }).first().click();
});

// A scanned stock's drawer, tab by tab
const firstHotStock = recommendations.hotStocks[0];
const candidate = `/recommendations/${firstHotStock.candidateId}`;
await shot('candidate-drawer-summary', () => open(candidate));
for (const [tab, name] of [['Momentum', 'momentum'], ['Analysts', 'analysts-and-news'], ['Company', 'company'],
  ['If I buy', 'if-i-buy']]) {
  await shot(`candidate-drawer-${name}`, async () => {
    await open(candidate);
    await clickTab(tab);
  });
}

// The Taxes page opened: a day, then a sale's buys; the year before
await shot('taxes-day-and-sale-opened', async () => {
  await open('/taxes');
  await page.getByText('02/09/2026').first().click();
  await page.waitForTimeout(400);
  await page.getByText(/^MRNA/).last().click();
}, { fullPage: true });
await shot('taxes-investor-tab', async () => {
  await open('/taxes');
  await page.getByRole('tab', { name: 'Dana' }).click();
}, { fullPage: true });
await shot('taxes-2025', async () => {
  await open('/taxes');
  await page.locator('select').first().selectOption({ label: '2025' });
}, { fullPage: true });

// The top bar's pieces
await shot('alerts-bell-open', async () => {
  await open('/');
  await page.getByRole('button', { name: 'Alerts' }).click();
});
await shot('alert-settings-drawer', async () => {
  await open('/');
  await page.getByRole('button', { name: 'Alerts' }).click();
  await page.getByRole('button', { name: 'Alert settings' }).click();
});
await shot('sync-now-failed-banner', async () => {
  await open('/');
  await page.getByRole('button', { name: 'Sync now' }).click();
  await page.getByText('The figures below are from the last sync').waitFor({ timeout: 30000 });
});

// The first load (its skeletons), with the portfolio held back for a few seconds — that one request only
async function openWhileThePortfolioLoads(path) {
  const holdBackForFourSeconds = async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 4000));
    // By then the next shot may have let it go already (`unrouteAll` below): nothing left to do.
    await route.continue().catch(() => {});
  };
  // The previous shot's hold may still be waiting: without this, it let this shot's data through at once.
  await page.unrouteAll({ behavior: 'ignoreErrors' });
  await page.route('**/api/portfolio', holdBackForFourSeconds, { times: 1 });
  await page.goto(`${UI}${path}`);
  await page.waitForTimeout(800);
}
await shot('first-load', () => openWhileThePortfolioLoads('/'));
await shot('first-load-positions', () => openWhileThePortfolioLoads('/positions'));
await shot('first-load-holding-drawer', () => openWhileThePortfolioLoads(nvda));

await browser.close();
