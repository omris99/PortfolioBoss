package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ai.AnalystAction;
import portfolioboss.ai.AnalystActionType;
import portfolioboss.ai.EventMagnitude;
import portfolioboss.ai.NewsEvent;
import portfolioboss.ai.NewsEventType;
import portfolioboss.model.WeighedAnalystMove;
import portfolioboss.model.WeighedNewsEvent;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The news score of RECOMMENDATIONS_TODO.md, decision 13, on made-up events and analysts' actions: a partnership or a
 * contract by its size, every other kind by kind, and an analyst's upgrade or downgrade only with a price target and
 * from the last 30 days.
 */
class NewsScoreCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @Test
    void aPartnershipOrAContractWeighsByItsSize() {
        assertThat(scoreOf(event(NewsEventType.PARTNERSHIP, EventMagnitude.HIGH))).isEqualTo(3);
        assertThat(scoreOf(event(NewsEventType.PARTNERSHIP, EventMagnitude.MEDIUM))).isEqualTo(2);
        assertThat(scoreOf(event(NewsEventType.MAJOR_CONTRACT, EventMagnitude.LOW))).isEqualTo(1);
    }

    /** Claude is asked for the size of every partnership; one without counts as the smallest. */
    @Test
    void aPartnershipWithoutASizeWeighsTheLeast() {
        assertThat(scoreOf(event(NewsEventType.PARTNERSHIP, null))).isEqualTo(1);
    }

    @Test
    void everyOtherKindWeighsTheSameWhateverItsSize() {
        assertThat(scoreOf(event(NewsEventType.GUIDANCE_RAISED, null))).isEqualTo(3);
        assertThat(scoreOf(event(NewsEventType.EARNINGS_BEAT, null))).isEqualTo(2);
        assertThat(scoreOf(event(NewsEventType.REGULATORY_APPROVAL, EventMagnitude.HIGH))).isEqualTo(2);
        assertThat(scoreOf(event(NewsEventType.PRODUCT_LAUNCH, null))).isEqualTo(1);
        assertThat(scoreOf(event(NewsEventType.GUIDANCE_CUT, null))).isEqualTo(-3);
        assertThat(scoreOf(event(NewsEventType.EARNINGS_MISS, null))).isEqualTo(-2);
        assertThat(scoreOf(event(NewsEventType.SHARE_OFFERING, null))).isEqualTo(-2);
        assertThat(scoreOf(event(NewsEventType.LAWSUIT_OR_REGULATION, null))).isEqualTo(-2);
    }

    /** The user's rule: an event no kind fits is never forced into the closest one — it is shown and weighs nothing. */
    @Test
    void anEventNoKindFitsWeighsNothing() {
        assertThat(scoreOf(event(NewsEventType.NOT_IN_THE_LIST, null))).isZero();
    }

    @Test
    void anUpgradeAddsTwoAndADowngradeTakesTwo() {
        assertThat(scoreOf(action(AnalystActionType.UPGRADE, 300.0, TODAY.minusDays(5)))).isEqualTo(2);
        assertThat(scoreOf(action(AnalystActionType.DOWNGRADE, 117.0, TODAY.minusDays(5)))).isEqualTo(-2);
    }

    /** Zacks, Weiss and Wall Street Zen grade without a target: their changes drop out, with no list of names. */
    @Test
    void aRatingChangeWithoutAPriceTargetWeighsNothing() {
        assertThat(scoreOf(action(AnalystActionType.UPGRADE, null, TODAY.minusDays(5)))).isZero();
    }

    @Test
    void onlyTheAnalystsMovesOfTheLast30DaysCount() {
        assertThat(scoreOf(action(AnalystActionType.DOWNGRADE, 117.0, TODAY.minusDays(30)))).isEqualTo(-2);
        assertThat(scoreOf(action(AnalystActionType.DOWNGRADE, 117.0, TODAY.minusDays(31)))).isZero();
        assertThat(scoreOf(action(AnalystActionType.DOWNGRADE, 117.0, null))).isZero();
    }

    /** A moved target or a repeated rating is in the analysts' trend, not here. */
    @Test
    void otherAnalystActionsWeighNothing() {
        assertThat(scoreOf(action(AnalystActionType.TARGET_RAISED, 300.0, TODAY.minusDays(5)))).isZero();
        assertThat(scoreOf(action(AnalystActionType.REITERATE, 300.0, TODAY.minusDays(5)))).isZero();
        assertThat(scoreOf(action(AnalystActionType.INITIATE, 300.0, TODAY.minusDays(5)))).isZero();
    }

    /** The good and the bad together: a partnership with a giant (+3), an offering, an upgrade and a downgrade. */
    @Test
    void theEventsAndTheMovesAddUp() {
        NewsScoreCalculator goodAndBad = new NewsScoreCalculator(
                List.of(event(NewsEventType.PARTNERSHIP, EventMagnitude.HIGH),
                        event(NewsEventType.SHARE_OFFERING, null)),
                List.of(action(AnalystActionType.UPGRADE, 300.0, TODAY.minusDays(7)),
                        action(AnalystActionType.DOWNGRADE, 219.0, TODAY.minusDays(14))),
                TODAY);

        assertThat(goodAndBad.score()).isEqualTo(3 - 2 + 2 - 2);
        assertThat(new NewsScoreCalculator(List.of(), List.of(), TODAY).score()).isZero();
    }

    /** What the page shows of a score: every event with its weight — one no kind fits too —, and the moves that count. */
    @Test
    void listsEveryEventWithItsWeightAndOnlyTheMovesThatCount() {
        NewsEvent partnership = event(NewsEventType.PARTNERSHIP, EventMagnitude.HIGH);
        NewsEvent noKindFits = event(NewsEventType.NOT_IN_THE_LIST, null);
        AnalystAction upgrade = action(AnalystActionType.UPGRADE, 300.0, TODAY.minusDays(7));
        AnalystAction targetRaised = action(AnalystActionType.TARGET_RAISED, 300.0, TODAY.minusDays(7));
        NewsScoreCalculator calculator = new NewsScoreCalculator(
                List.of(partnership, noKindFits), List.of(upgrade, targetRaised), TODAY);

        assertThat(calculator.weighedNewsEvents())
                .containsExactly(new WeighedNewsEvent(partnership, 3), new WeighedNewsEvent(noKindFits, 0));
        assertThat(calculator.countedAnalystMoves()).containsExactly(new WeighedAnalystMove(upgrade, 2));
        assertThat(calculator.score()).isEqualTo(5);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private int scoreOf(NewsEvent event) {
        return new NewsScoreCalculator(List.of(event), List.of(), TODAY).score();
    }

    private int scoreOf(AnalystAction action) {
        return new NewsScoreCalculator(List.of(), List.of(action), TODAY).score();
    }

    private NewsEvent event(NewsEventType type, EventMagnitude magnitude) {
        return new NewsEvent(type, magnitude, "Something happened.", "A headline", "https://example.com/news");
    }

    private AnalystAction action(AnalystActionType type, Double priceTarget, LocalDate date) {
        return new AnalystAction(date, "Some Firm", type, "Buy", "Sell", null, priceTarget,
                "https://example.com/action", null);
    }
}
