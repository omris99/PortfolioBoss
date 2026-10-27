package portfolioboss.ai;

/**
 * What kind of event a news result reports about the stock (RECOMMENDATIONS_TODO.md, decision 13). Claude says only the
 * kind; whether it is good or bad for the stock is the code's, by the kind — the first six are good, the next four bad,
 * and {@link #NOT_IN_THE_LIST} neither. An analyst's upgrade or downgrade is not one of them: it is in
 * {@link StockAnalysisResult#recentActions}, with the firm's name, where the code finds it (in the stability check of
 * 2026-10-27 Claude gave the same downgrade a different magnitude each time, and once an "upgrade" no firm had made).
 */
public enum NewsEventType {
    PARTNERSHIP,
    MAJOR_CONTRACT,
    PRODUCT_LAUNCH,
    GUIDANCE_RAISED,
    EARNINGS_BEAT,
    /** A regulator's approval or clearance — the FDA's, for example. */
    REGULATORY_APPROVAL,
    GUIDANCE_CUT,
    EARNINGS_MISS,
    /** New shares sold by the company: each existing share owns less of it. Debt — notes, bonds, loans — is not. */
    SHARE_OFFERING,
    /** A lawsuit, an investigation, a fine, or a regulator's refusal. */
    LAWSUIT_OR_REGULATION,
    /**
     * An event about the company that none of the other kinds fits — never forced into the closest one (the user's
     * rule, 2026-10-27, after an FDA approval came back as a partnership). It weighs nothing; it is shown.
     */
    NOT_IN_THE_LIST
}
