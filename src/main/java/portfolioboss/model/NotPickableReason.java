package portfolioboss.model;

/**
 * Why the recommendation can't pick a stock (RECOMMENDATIONS_TODO.md, decisions 5 and 14), in the order the checks
 * run: the analysts' target first — known for every stock before anything is paid for —, then Finnhub's figures. Goes
 * into the JSON as it is, so the names are JSON values.
 */
public enum NotPickableReason {
    /** Fewer than three firms gave a target in the half year before the scan. */
    NO_ANALYST_TARGET,
    /** The analysts' median target is less than 15% above the last close — or below it. */
    TARGET_TOO_CLOSE,
    /** Finnhub gave none of its four figures: four of the five risk signs were never checked. */
    NO_COMPANY_FACTS,
    /** The risk signs weigh more than 2 together. */
    RISK_SIGNS_TOO_HEAVY
}
