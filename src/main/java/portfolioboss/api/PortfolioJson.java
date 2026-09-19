package portfolioboss.api;

import portfolioboss.model.Holding;
import portfolioboss.model.PortfolioSnapshot;

/**
 * Hand-written JSON for the API. The payload is one flat list, so no JSON library is needed until
 * the Spring Boot move. Figures IB did not report ({@code NaN}) are written as {@code null}.
 */
final class PortfolioJson {

    static String toJson(PortfolioSnapshot snapshot) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\"account\":").append(quote(snapshot.account()))
                .append(",\"asOf\":").append(quote(snapshot.asOf().toString()))
                .append(",\"netLiquidation\":").append(number(snapshot.netLiquidation()))
                .append(",\"totalCashValue\":").append(number(snapshot.totalCashValue()))
                .append(",\"holdings\":[");
        boolean isFirstHolding = true;
        for (Holding holding : snapshot.holdings()) {
            if (!isFirstHolding) {
                json.append(',');
            }
            appendHolding(json, holding);
            isFirstHolding = false;
        }
        return json.append("]}").toString();
    }

    private static void appendHolding(StringBuilder json, Holding holding) {
        json.append("{\"symbol\":").append(quote(holding.symbol()))
                .append(",\"secType\":").append(quote(holding.secType()))
                .append(",\"currency\":").append(quote(holding.currency()))
                .append(",\"position\":").append(number(holding.position()))
                .append(",\"averageCost\":").append(number(holding.averageCost()))
                .append(",\"marketPrice\":").append(number(holding.marketPrice()))
                .append(",\"marketValue\":").append(number(holding.marketValue()))
                .append(",\"unrealizedPnl\":").append(number(holding.unrealizedPnl()))
                .append(",\"realizedPnl\":").append(number(holding.realizedPnl()))
                .append(",\"account\":").append(quote(holding.account()))
                .append(",\"costBasis\":").append(number(holding.costBasis()))
                .append(",\"unrealizedPnlPercent\":").append(number(holding.unrealizedPnlPercent()))
                .append('}');
    }

    private static String number(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }

    private static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder quoted = new StringBuilder(text.length() + 2).append('"');
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    boolean isControlCharacter = character < 0x20;
                    if (isControlCharacter) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private PortfolioJson() {
    }
}
