package portfolioboss.api.portfolio.response;

import portfolioboss.calculation.portfolio.TradeFact;

import java.math.BigDecimal;

/**
 * A trade a split translated, as it counts after the splits (STOCK_SPLITS_TODO.md): what the drawer shows under the trade
 * as typed — "= 40 at $100 after the split". The trade itself, as typed, is its {@link TradeResponse}, whose
 * {@code tradeId} this is: the edit form shows and sends only that, so that a translated number is never stored.
 */
public record TranslatedTradeResponse(long tradeId, BigDecimal quantity, BigDecimal price) {

    public TranslatedTradeResponse(TradeFact translatedTrade) {
        this(translatedTrade.id(), translatedTrade.quantity(), translatedTrade.price());
    }
}
