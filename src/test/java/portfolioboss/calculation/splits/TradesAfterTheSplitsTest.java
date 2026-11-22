package portfolioboss.calculation.splits;

import org.junit.jupiter.api.Test;
import portfolioboss.calculation.portfolio.HoldingHistory;
import portfolioboss.calculation.portfolio.TradeFact;
import portfolioboss.model.portfolio.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The trades typed in for a holding, as they count after its splits (STOCK_SPLITS_TODO.md, decision 7): only every
 * investor's open position at a split is translated. Made-up trades, a split of 4 for 1 on 10 March 2026.
 */
class TradesAfterTheSplitsTest {

    private static final long ACCOUNT_OWNER_ID = 1;
    private static final long OTHER_INVESTOR_ID = 2;
    private static final int CON_ID = 1111;
    private static final LocalDate SPLIT_DAY = LocalDate.of(2026, 3, 10);

    private final StockSplit fourForOne =
            new StockSplit("A1", "WXYZ", CON_ID, CON_ID, SPLIT_DAY.atTime(20, 25), 1, 4);

    /** 10 bought at $400 before the split: 40 at $100, the commission as it was — and IB's 40 shares match. */
    @Test
    void aBuyHeldThroughTheSplitIsTranslated() {
        TradeFact buy = tradeOf(11, ACCOUNT_OWNER_ID, LocalDate.of(2026, 1, 5), TradeSide.BUY, "10", "400");

        TradesAfterTheSplits trades = new TradesAfterTheSplits(List.of(buy), List.of(fourForOne));

        assertThat(trades.allTrades()).singleElement().satisfies(translated -> {
            assertThat(translated.id()).isEqualTo(11L);
            assertThat(translated.quantity()).isEqualByComparingTo("40");
            assertThat(translated.price()).isEqualByComparingTo("100");
            assertThat(translated.commission()).isEqualByComparingTo("5");
            assertThat(translated.date()).isEqualTo(buy.date());
        });
        assertThat(HoldingHistory.of(trades.allTrades()).netQuantity()).isEqualByComparingTo("40");
    }

    /** IB books the split after the market closed: a trade of its day was before it, one of the next day after it. */
    @Test
    void aTradeOfTheSplitsDayIsTranslatedAndOneOfTheNextDayIsNot() {
        TradeFact onTheSplitsDay = tradeOf(11, ACCOUNT_OWNER_ID, SPLIT_DAY, TradeSide.BUY, "10", "400");
        TradeFact onTheNextDay = tradeOf(12, ACCOUNT_OWNER_ID, SPLIT_DAY.plusDays(1), TradeSide.BUY, "8", "101");

        List<TradeFact> trades = new TradesAfterTheSplits(List.of(onTheSplitsDay, onTheNextDay), List.of(fourForOne))
                .allTrades();

        assertThat(trades.get(0).quantity()).isEqualByComparingTo("40");
        assertThat(trades.get(1)).isEqualTo(onTheNextDay);
    }

    /**
     * Bought 10 in 2025 and sold all of them, bought 5 again in 2026, then the split: IB reports no split of shares no
     * longer held, so the position sold in full stays as it happened, and only the 5 become 20.
     */
    @Test
    void aPositionSoldInFullBeforeTheSplitStaysAsItHappened() {
        TradeFact firstBuy = tradeOf(11, ACCOUNT_OWNER_ID, LocalDate.of(2025, 2, 3), TradeSide.BUY, "10", "400");
        TradeFact sale = tradeOf(12, ACCOUNT_OWNER_ID, LocalDate.of(2025, 6, 2), TradeSide.SELL, "10", "450");
        TradeFact buyAgain = tradeOf(13, ACCOUNT_OWNER_ID, LocalDate.of(2026, 1, 5), TradeSide.BUY, "5", "500");

        TradesAfterTheSplits trades = new TradesAfterTheSplits(List.of(firstBuy, sale, buyAgain), List.of(fourForOne));

        assertThat(trades.allTrades().subList(0, 2)).containsExactly(firstBuy, sale);
        assertThat(trades.allTrades().get(2).quantity()).isEqualByComparingTo("20");
        assertThat(trades.allTrades().get(2).price()).isEqualByComparingTo("125");
        assertThat(trades.translatedTrades()).extracting(TradeFact::id).containsExactly(13L);
    }

    /** Bought 10, sold 4 before the split: both are of the open position, so the 6 left become IB's 24. */
    @Test
    void aPartSoldBeforeTheSplitIsTranslatedWithTheOpenPosition() {
        TradeFact buy = tradeOf(11, ACCOUNT_OWNER_ID, LocalDate.of(2026, 1, 5), TradeSide.BUY, "10", "400");
        TradeFact partSold = tradeOf(12, ACCOUNT_OWNER_ID, LocalDate.of(2026, 2, 2), TradeSide.SELL, "4", "420");

        List<TradeFact> trades = new TradesAfterTheSplits(List.of(buy, partSold), List.of(fourForOne)).allTrades();

        assertThat(trades.get(1).quantity()).isEqualByComparingTo("16");
        assertThat(trades.get(1).price()).isEqualByComparingTo("105");
        assertThat(HoldingHistory.of(trades).netQuantity()).isEqualByComparingTo("24");
    }

    /** The other investor sold all of theirs before the split, the account owner held: only the owner's is translated. */
    @Test
    void eachInvestorsOpenPositionIsTheirOwn() {
        TradeFact ownersBuy = tradeOf(11, ACCOUNT_OWNER_ID, LocalDate.of(2026, 1, 5), TradeSide.BUY, "10", "400");
        TradeFact othersBuy = tradeOf(12, OTHER_INVESTOR_ID, LocalDate.of(2026, 1, 6), TradeSide.BUY, "3", "400");
        TradeFact othersSale = tradeOf(13, OTHER_INVESTOR_ID, LocalDate.of(2026, 2, 2), TradeSide.SELL, "3", "420");

        TradesAfterTheSplits trades =
                new TradesAfterTheSplits(List.of(ownersBuy, othersBuy, othersSale), List.of(fourForOne));

        assertThat(trades.translatedTrades()).extracting(TradeFact::id).containsExactly(11L);
        assertThat(trades.allTrades().subList(1, 3)).containsExactly(othersBuy, othersSale);
    }

    /** A split of 2 for 1, then one of 4 for 1: 10 shares held through both are 80, at an eighth of the price. */
    @Test
    void twoSplitsTranslateTheSharesHeldThroughBoth() {
        StockSplit twoForOne = new StockSplit("A0", "WXYZ", CON_ID, CON_ID, LocalDateTime.of(2025, 6, 2, 20, 25), 1, 2);
        TradeFact buy = tradeOf(11, ACCOUNT_OWNER_ID, LocalDate.of(2025, 1, 6), TradeSide.BUY, "10", "800");
        TradeFact buyBetween = tradeOf(12, ACCOUNT_OWNER_ID, LocalDate.of(2025, 9, 1), TradeSide.BUY, "5", "400");

        List<TradeFact> trades =
                new TradesAfterTheSplits(List.of(buy, buyBetween), List.of(twoForOne, fourForOne)).allTrades();

        assertThat(trades.get(0).quantity()).isEqualByComparingTo("80");
        assertThat(trades.get(0).price()).isEqualByComparingTo("100");
        assertThat(trades.get(1).quantity()).isEqualByComparingTo("20");
        assertThat(trades.get(1).price()).isEqualByComparingTo("100");
    }

    @Test
    void withoutASplitEveryTradeIsAsTyped() {
        TradeFact buy = tradeOf(11, ACCOUNT_OWNER_ID, LocalDate.of(2026, 1, 5), TradeSide.BUY, "10", "400");

        TradesAfterTheSplits trades = new TradesAfterTheSplits(List.of(buy), List.of());

        assertThat(trades.allTrades()).containsExactly(buy);
        assertThat(trades.translatedTrades()).isEmpty();
    }

    private TradeFact tradeOf(long id, long investorId, LocalDate date, TradeSide side, String quantity, String price) {
        return new TradeFact(id, investorId, date, side, new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal("5"));
    }
}
