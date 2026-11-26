import type { Holding } from '../../types/portfolio';
import { MedianTargetWithUpside } from '../AnalystTarget';
import { NextEarningsDate } from '../companyInfo/TimingNote';
import { HoldingCompanyRisks } from '../HoldingWarningSigns';
import { LabeledValue } from '../LabeledValue';
import { MomentumMark } from '../Momentum';
import { SignalWords } from '../Signal';

/**
 * The line under each holding of the Averaging calculator: what PortfolioBoss knows before buying more. TODO.md's
 * principle 2 asks "if you held none of it, would you buy it today at this price?", and the answer is data, not a
 * written thesis (MY_HOLDINGS_TODO.md, decision 1) — each as the holding's drawer shows it. Round 6 (DESIGN_BRIEF.md
 * §11.6): five slots on a fixed grid, each label bold over its value, so that the slots line up from row to row; the
 * Signal dot's warning signs and the company risks in two slots, under names that can't be confused.
 *
 * In a box of its own, a soft grey from top to bottom (2026-11-25, the user's pick over the design's bare line): on the
 * row's own white, its bold labels read as a second header row of the table, and its slots as columns that don't line up
 * with the ones above. Company risks has the widest slot; each risk, and each of the Signal's warning signs, on a line of
 * its own, so that none breaks midway.
 */
export function WhatToKnowLine({ holding }: { holding: Holding }) {
  return (
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_minmax(0,0.8fr)_minmax(0,1.2fr)_minmax(0,0.9fr)] gap-x-5 gap-y-1 rounded-md bg-gradient-to-b from-what-to-know-top to-what-to-know-bottom px-3 py-2 text-[13px] leading-5">
      <LabeledValue label="Signal" stacked>
        <SignalWords holding={holding} stacked />
      </LabeledValue>
      <LabeledValue label="Company risks" stacked>
        <HoldingCompanyRisks holding={holding} stacked />
      </LabeledValue>
      <LabeledValue label="Momentum" stacked>
        <MomentumMark momentum={holding.momentum} />
      </LabeledValue>
      <LabeledValue label="Analysts’ target" stacked>
        <MedianTargetWithUpside analystTarget={holding.analystTarget} />
      </LabeledValue>
      <LabeledValue label="Earnings" stacked>
        <NextEarningsDate companyInfo={holding.companyInfo} markPlacement="beside" />
      </LabeledValue>
    </div>
  );
}
