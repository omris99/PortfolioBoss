package portfolioboss.exchangerates;

import org.junit.jupiter.api.Test;
import portfolioboss.exceptions.ExchangeRatesUnavailableException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The CSV the Bank of Israel answers with, in the shape of its real answer (2026-11-15) — public rates, nothing of the
 * portfolio: the dollar's rates around Yom Kippur 2025, when it published none on 01.10 and 02.10.
 */
class BankOfIsraelRatesReaderTest {

    private static final String HEADER = "SERIES_CODE,FREQ,BASE_CURRENCY,COUNTER_CURRENCY,UNIT_MEASURE,DATA_TYPE,"
            + "DATA_SOURCE,TIME_COLLECT,CONF_STATUS,PUB_WEBSITE,UNIT_MULT,COMMENTS,TIME_PERIOD,OBS_VALUE,RELEASE_STATUS";
    private static final String ANSWER = HEADER + "\n"
            + "RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-09-30,3.306,YP\n"
            + "RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-10-03,3.315,YP\n"
            + "RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-10-06,3.28,YP\n";

    private final BankOfIsraelRatesReader ratesReader = new BankOfIsraelRatesReader();

    @Test
    void readsTheDayAndTheRateOfEveryLine() {
        List<BankOfIsraelRate> rates = ratesReader.readRates(ANSWER);

        assertThat(rates).containsExactly(
                new BankOfIsraelRate(LocalDate.of(2025, 9, 30), new BigDecimal("3.306")),
                new BankOfIsraelRate(LocalDate.of(2025, 10, 3), new BigDecimal("3.315")),
                new BankOfIsraelRate(LocalDate.of(2025, 10, 6), new BigDecimal("3.28")));
    }

    @Test
    void findsTheColumnsByTheirNamesWhereverTheyAre() {
        String answerWithTheColumnsMoved = """
                OBS_VALUE,SERIES_CODE,TIME_PERIOD
                3.306,RER_USD_ILS,2025-09-30
                """;

        assertThat(ratesReader.readRates(answerWithTheColumnsMoved))
                .containsExactly(new BankOfIsraelRate(LocalDate.of(2025, 9, 30), new BigDecimal("3.306")));
    }

    @Test
    void aHeaderAloneIsNoRate() {
        assertThat(ratesReader.readRates(HEADER + "\n")).isEmpty();
    }

    /** A comma inside a quoted comment would shift every column after it: the rate read would be another field. */
    @Test
    void aLineWithMoreFieldsThanTheHeaderFailsNamingTheLine() {
        String answer = HEADER + "\n"
                + "RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-09-30,3.306,YP\n"
                + "RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,\"late, corrected\",2025-10-03,3.315,YP\n";

        assertThatThrownBy(() -> ratesReader.readRates(answer))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("line 3 of the Bank of Israel's answer has 16 fields, its header 15");
    }

    @Test
    void aRateThatIsNotANumberFailsNamingTheLine() {
        String answer = HEADER + "\n" + "RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-09-30,NaN?,YP\n";

        assertThatThrownBy(() -> ratesReader.readRates(answer))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("line 2 of the Bank of Israel's answer has an OBS_VALUE that isn't a number: \"NaN?\"");
    }

    @Test
    void aDayThatIsNotADateFailsNamingTheLine() {
        String answer = HEADER + "\n" + "RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-Q3,3.306,YP\n";

        assertThatThrownBy(() -> ratesReader.readRates(answer))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("line 2 of the Bank of Israel's answer has a TIME_PERIOD that isn't a date: \"2025-Q3\"");
    }

    @Test
    void anAnswerWithoutTheRateColumnFails() {
        assertThatThrownBy(() -> ratesReader.readRates("SERIES_CODE,TIME_PERIOD\nRER_USD_ILS,2025-09-30\n"))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("the Bank of Israel's answer has no OBS_VALUE column");
    }

    @Test
    void anEmptyAnswerFails() {
        assertThatThrownBy(() -> ratesReader.readRates(""))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("the Bank of Israel's answer is empty");
    }
}
