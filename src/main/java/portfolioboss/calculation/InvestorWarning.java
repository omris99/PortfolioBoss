package portfolioboss.calculation;

/**
 * Something to check in what was entered for an investor, shown on their card — never a reason to reject an entry. An
 * investor can have several at once, unlike a holding. Part of the JSON the UI receives
 * ({@code InvestorResponse.warnings}), so the component names are JSON keys and must stay stable. {@code message} is in
 * English and the UI shows it as it is.
 */
public record InvestorWarning(InvestorWarningType type, String message) {
}
