package portfolioboss.sources.finviz;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * The three screens of the Recommendations page's lists (RECOMMENDATIONS_FINVIZ_TODO.md, decision 12): each sends the
 * user's own filters and order — copied here from his addresses, so that a filter changed by mistake fails —, and its
 * rows are read into {@link FinvizStock}s. {@link FinvizClient} is a stand-in.
 */
class FinvizScreensTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private final FinvizClient finvizClient = mock(FinvizClient.class);
    private final FinvizScreens finvizScreens = new FinvizScreens(finvizClient);

    @Test
    void eachScreenHasTheUsersFiltersAndOrder() {
        assertThat(FinvizScreen.FAST_GROWTH.filters())
                .isEqualTo("cap_smallover,fa_grossmargin_o50,fa_pe_o50,fa_salesqoq_o30");
        assertThat(FinvizScreen.FAST_GROWTH.order()).isEqualTo("-sales5years");
        assertThat(FinvizScreen.SLOW_GROWTH.filters())
                .isEqualTo("cap_smallover,fa_fpe_profitable,fa_sales5years_o10,fa_salesqoq_o5,fa_opermargin_o10,fa_ps_u5");
        assertThat(FinvizScreen.SLOW_GROWTH.order()).isNull();
        assertThat(FinvizScreen.VALUE.filters()).isEqualTo("cap_midover,fa_eps5years_o5,fa_fpe_u20,fa_netmargin_o5,"
                + "fa_opermargin_o10,fa_pfcf_u20,fa_sales5years_o5,sh_price_o5");
        assertThat(FinvizScreen.VALUE.order()).isNull();
    }

    /** A screen of every stock its filters pass: no symbols are named. */
    @Test
    void aScreenIsAskedForWithItsFiltersAndOrderAndItsRowsAreRead() {
        given(finvizClient.screenAsCsv(FinvizScreen.FAST_GROWTH.filters(), "-sales5years", List.of()))
                .willReturn(screenOfOneStock("VELQ"));

        List<FinvizStock> stocks = finvizScreens.stocksOf(FinvizScreen.FAST_GROWTH, TODAY);

        assertThat(stocks).extracting(FinvizStock::symbol).containsExactly("VELQ");
    }

    @Test
    void aScreenWithoutAnOrderSendsNone() {
        given(finvizClient.screenAsCsv(anyString(), isNull(), anyList())).willReturn(screenOfOneStock("MRWS"));

        assertThat(finvizScreens.stocksOf(FinvizScreen.VALUE, TODAY)).extracting(FinvizStock::symbol)
                .containsExactly("MRWS");
    }

    /** A made-up stock with every column the reader needs. */
    private String screenOfOneStock(String ticker) {
        return "\"Ticker\",\"Company\",\"Sector\",\"Industry\",\"Market Cap\",\"Price\",\"P/E\",\"Forward P/E\","
                + "\"PEG\",\"P/S\",\"P/B\",\"P/Free Cash Flow\",\"EPS growth past 5 years\",\"Sales growth past 5 years\","
                + "\"Sales growth quarter over quarter\",\"Gross Margin\",\"Operating Margin\",\"Net Profit Margin\","
                + "\"Total Debt/Equity\",\"Earnings Date\"\n"
                + "\"" + ticker + "\",\"" + ticker + " Inc\",\"Healthcare\",\"Biotechnology\",\"10333.98\",\"68.37\","
                + "\"101.54\",\"24.66\",\"2.10\",\"11.51\",\"9.30\",\"120.78\",\"-\",\"426.73%\",\"60.74%\",\"82.74%\",\"21.48%\","
                + "\"11.81%\",\"0.35\",\"-\"\n";
    }
}
