// Visits every page and drawer of the demo app and lists any visible text still written as an ISO date (2026-11-22):
// every date on screen is dd/mm/yyyy since round 6 (DESIGN_BRIEF.md §11.6, rule 5). "ok" per screen, "ISO" with what it
// found. The app must be running on the demo database.
//   node find-iso-dates.mjs
import { chromium } from 'playwright-core';

const UI = 'http://localhost:5174';
const API = 'http://localhost:8080/api';
const portfolio = await (await fetch(`${API}/portfolio`)).json();
if (portfolio.account !== 'U1234567') throw new Error('not the demo account');
const recommendations = await (await fetch(`${API}/recommendations`)).json();

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
await context.route('**/api/**', (route) => (route.request().method() === 'GET' ? route.continue() : route.abort()));
const page = await context.newPage();
const ISO_DATE = /\b\d{4}-\d{2}-\d{2}\b/g;

async function check(name, path, prepare = async () => {}) {
  await page.goto(`${UI}${path}`);
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(300);
  try { await prepare(); await page.waitForTimeout(300); } catch (error) { console.log(`  (${name}: ${error.message.split('\n')[0]})`); }
  const text = await page.evaluate(() => document.body.innerText);
  const found = [...new Set(text.match(ISO_DATE) ?? [])];
  console.log(`${found.length === 0 ? 'ok  ' : 'ISO '} ${name}${found.length === 0 ? '' : ': ' + found.slice(0, 6).join(', ')}`);
}
const tab = (name) => page.getByRole('tab', { name, exact: false }).first().click();
const holding = (symbol) => `/positions/${portfolio.holdings.find((h) => h.symbol === symbol).id}`;

for (const path of ['/', '/positions', '/averaging', '/closed', '/investors', '/recommendations', '/taxes']) await check(path, path);
for (const tabName of ['Summary', 'Momentum', 'Analysts', 'Company', 'Trades', 'Weight']) await check(`NVDA ${tabName}`, holding('NVDA'), () => tab(tabName));
await check('TEAM By investor', holding('TEAM'), () => tab('By investor'));
await check('closed drawer', '/closed', () => page.locator('tbody tr').first().click());
await check('closed drawer trades', '/closed', async () => { await page.locator('tbody tr').first().click(); await tab('Trades'); });
await check('investor deposits', '/investors', () => page.getByText(/Deposits/).first().click());
const candidate = `/recommendations/${recommendations.hotStocks[0].candidateId}`;
for (const tabName of ['Summary', 'Momentum', 'Analysts', 'Company', 'If I buy']) await check(`candidate ${tabName}`, candidate, () => tab(tabName));
await check('taxes day opened', '/taxes', async () => { await page.getByText('02/09/2026').first().click(); await page.waitForTimeout(300); await page.getByText(/^MRNA/).last().click(); });
await check('taxes 2025', '/taxes', () => page.locator('select').first().selectOption({ label: '2025' }));
await check('alerts', '/', () => page.getByRole('button', { name: /^Alerts/ }).click());
await check('trade modal', holding('NVDA'), async () => { await tab('Trades'); await page.getByRole('button', { name: 'Add trade' }).click(); });
await browser.close();
