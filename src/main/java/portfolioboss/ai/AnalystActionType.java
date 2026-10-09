package portfolioboss.ai;

/** What one analyst firm did: changed its rating, started covering the stock, kept it, or moved its price target. */
public enum AnalystActionType {
    UPGRADE,
    DOWNGRADE,
    INITIATE,
    REITERATE,
    TARGET_RAISED,
    TARGET_LOWERED
}
