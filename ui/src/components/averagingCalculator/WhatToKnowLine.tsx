import type { ReactNode } from 'react';
import type { Holding } from '../../types/portfolio';
import { MedianTargetWithUpside } from '../AnalystTarget';
import { NextEarningsDate } from '../companyInfo/TimingNote';
import { HoldingWarningSigns } from '../HoldingWarningSigns';
import { MomentumChip } from '../Momentum';

/** One labeled part of the line: "Momentum WEAK 1/5". */
function LinePart({ label, children }: { label: string; children: ReactNode }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <span className="text-muted">{label}</span>
      {children}
    </span>
  );
}

/**
 * The line under each holding of the Averaging calculator: what PortfolioBoss knows before buying more. TODO.md's
 * principle 2 asks "if you held none of it, would you buy it today at this price?", and the answer is data, not a
 * written thesis (MY_HOLDINGS_TODO.md, decision 1) — the warning signs, the momentum, the analysts' target and the next
 * report, each as the holding's drawer shows it.
 */
export function WhatToKnowLine({ holding }: { holding: Holding }) {
  return (
    <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5 text-caption leading-[18px]">
      <LinePart label="Warning signs">
        <HoldingWarningSigns holding={holding} />
      </LinePart>
      <LinePart label="Momentum">
        <MomentumChip momentum={holding.momentum} />
      </LinePart>
      <LinePart label="Analysts’ target">
        <MedianTargetWithUpside analystTarget={holding.analystTarget} />
      </LinePart>
      <LinePart label="Earnings">
        <NextEarningsDate companyInfo={holding.companyInfo} markPlacement="beside" />
      </LinePart>
    </div>
  );
}
