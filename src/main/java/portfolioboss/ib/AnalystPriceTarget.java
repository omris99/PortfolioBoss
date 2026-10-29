package portfolioboss.ib;

import java.time.LocalDateTime;

/**
 * One firm's price target for a stock, read from one Dow Jones headline (RECOMMENDATIONS_TODO.md, decision 14), e.g.
 * "Snowflake Price Target Raised to $450.00/Share From $300.00 by Piper Sandler". It is in the price terms of its own
 * day: after a stock split an older target is several times too high — which is why the targets are combined by their
 * median ({@code calculation.AnalystTargetCalculator}).
 *
 * @param publishedAt         when the headline came out
 * @param firm                the firm, as Dow Jones names it: "Piper Sandler", "B of A Securities"
 * @param priceTarget         the target, in dollars
 * @param previousPriceTarget the target it replaced; {@code null} when the headline names none ("Maintained With a",
 *                            "Announced at")
 * @param headline            the headline it was read from, without IB's metadata
 */
public record AnalystPriceTarget(LocalDateTime publishedAt, String firm, double priceTarget, Double previousPriceTarget,
                                 String headline) {

    /**
     * How long a firm's target counts (decision 14: half a year — two quarterly reports, so that a firm which skipped
     * one update still counts). The scan reads the headlines this far back, and the median is taken over these months.
     */
    public static final int MONTHS_COUNTED = 6;
}
