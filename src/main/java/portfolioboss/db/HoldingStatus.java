package portfolioboss.db;

/** Whether TWS still reports the position. Stored as its name, so the values read plainly in psql. */
public enum HoldingStatus {
    OPEN,
    CLOSED
}
