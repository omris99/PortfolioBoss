// Positions at 1280 and 1440 with every column on: does the table fit its card without scrolling sideways (round 6,
// DESIGN_BRIEF.md §11.6, rule 3)? Prints "fits" or "SCROLLS" per width and saves a screenshot of each. The keys are
// PositionsTable.tsx's POSITION_COLUMNS: change both together.
//   node all-columns.mjs <output folder>
import { chromium } from 'playwright-core';

const OUTPUT = process.argv[2];
const ALL_KEYS = ['symbol', 'sector', 'signal', 'momentum', 'position', 'marketValue', 'unrealizedPnl', 'holdingDays',
  'averageCostAndLast', 'analystTarget', 'earnings', 'boughtAndLastSold'];
const browser = await chromium.launch({ channel: 'chrome', headless: true });
for (const width of [1280, 1440]) {
  const context = await browser.newContext({ viewport: { width, height: 900 } });
  await context.addInitScript((keys) => localStorage.setItem('portfolioboss.positions.visibleColumns.v2', JSON.stringify(keys)), ALL_KEYS);
  const page = await context.newPage();
  await page.goto('http://localhost:5174/positions');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);
  const sizes = await page.evaluate(() => {
    const scroller = document.querySelector('table')?.parentElement;
    return { scrollWidth: scroller?.scrollWidth, clientWidth: scroller?.clientWidth };
  });
  console.log(`${width}: table ${sizes.scrollWidth}px in a box of ${sizes.clientWidth}px — ${sizes.scrollWidth > sizes.clientWidth ? 'SCROLLS' : 'fits'}`);
  await page.screenshot({ path: `${OUTPUT}/positions-all-columns-${width}.png` });
}
await browser.close();
