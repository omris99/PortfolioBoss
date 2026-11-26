package portfolioboss.sources.finnhub;

import org.junit.jupiter.api.Test;
import portfolioboss.sources.CompanyFacts;
import portfolioboss.sources.Stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Finnhub as the second source of the company figures (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9), with
 * {@link FinnhubClient} a stand-in: each stock from its own call, and a stock whose call failed left out of the answer,
 * the others' answers kept.
 */
class FinnhubCompanyFiguresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final Stock ABCD = new Stock("ABCD", null, new CompanyFacts(12.5, 20.1, 0.4, 31.0), "Finnhub");

    private final FinnhubClient finnhubClient = mock(FinnhubClient.class);
    private final FinnhubCompanyFigures finnhubCompanyFigures = new FinnhubCompanyFigures(finnhubClient);

    @Test
    void eachStockIsAnsweredFromItsOwnCallAndOneThatFailedIsLeftOut() {
        given(finnhubClient.stockOrNullOnFailure(eq("ABCD"), eq(TODAY))).willReturn(ABCD);
        given(finnhubClient.stockOrNullOnFailure(eq("WXYZ"), eq(TODAY))).willReturn(null);

        Map<String, Stock> answers = finnhubCompanyFigures.answersFor(List.of("ABCD", "WXYZ"), TODAY);

        assertThat(answers).containsExactly(Map.entry("ABCD", ABCD));
    }

    @Test
    void itIsConnectedWhileFinnhubsKeyIsSet() {
        given(finnhubClient.hasApiKey()).willReturn(true);
        assertThat(finnhubCompanyFigures.isConnected()).isTrue();

        given(finnhubClient.hasApiKey()).willReturn(false);
        assertThat(finnhubCompanyFigures.isConnected()).isFalse();
        assertThat(finnhubCompanyFigures.sourceName()).isEqualTo("Finnhub");
    }
}
