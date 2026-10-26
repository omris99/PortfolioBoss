/**
 * One of the five risk signs (RECOMMENDATIONS_TODO.md, decision 5), in the warning-sign tone, in sentence case — the
 * words of the brief, never capitals. Its hover names the rule it broke. Before the analysis only Recovering is known.
 */
export function RiskSignTag({ name, rule }: { name: string; rule: string }) {
  return (
    <span
      title={`${name} · a risk sign: ${rule}`}
      className="inline-flex h-[18px] items-center whitespace-nowrap rounded-sm bg-negative-soft px-[5px] text-[11px] font-medium text-negative"
    >
      {name}
    </span>
  );
}

/** Lower than a year ago — one of the five risk signs, so it wears their tag everywhere. */
export function RecoveringTag() {
  return (
    <RiskSignTag
      name="Recovering"
      rule="lower than a year ago. After a crash the averages sink with the price, so a rebound looks like a trend fast."
    />
  );
}
