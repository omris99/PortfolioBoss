package portfolioboss.model;

/**
 * A gap between the trades entered by hand and what IB reports, shown next to the holding — never a reason to
 * reject a trade. It is part of the JSON the UI receives ({@code HoldingResponse.warnings}), so the component names
 * are JSON keys and must stay stable. {@code message} is in English and the UI shows it as it is.
 */
public record HoldingWarning(HoldingWarningType type, String message) {
}
