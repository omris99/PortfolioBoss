package portfolioboss.sources;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asking the sources of one kind of data in their order (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9), on made-up
 * sources whose answer for a stock is a word: the first that answers for a stock gives its answer, a source that is off
 * is skipped, one that fails or doesn't know a stock passes it to the next, and a whole stock moves, never a figure.
 */
class SourcesInOrderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void theFirstSourceAnswersAndTheSecondIsAskedOnlyAboutTheRest() {
        FakeSource first = new FakeSource("First", true, Map.of("ABCD", "first's ABCD"));
        FakeSource second = new FakeSource("Second", true, Map.of("ABCD", "second's ABCD", "WXYZ", "second's WXYZ"));

        Map<String, String> answers = sourcesInOrder(first, second).answersFor(List.of("ABCD", "WXYZ"), TODAY);

        assertThat(answers).containsExactly(Map.entry("ABCD", "first's ABCD"), Map.entry("WXYZ", "second's WXYZ"));
        assertThat(first.askedAbout()).containsExactly(List.of("ABCD", "WXYZ"));
        assertThat(second.askedAbout()).containsExactly(List.of("WXYZ"));
    }

    @Test
    void aSourceThatIsOffIsNeverAsked() {
        FakeSource off = new FakeSource("Off", false, Map.of("ABCD", "off's ABCD"));
        FakeSource on = new FakeSource("On", true, Map.of("ABCD", "on's ABCD"));

        Map<String, String> answers = sourcesInOrder(off, on).answersFor(List.of("ABCD"), TODAY);

        assertThat(answers).containsExactly(Map.entry("ABCD", "on's ABCD"));
        assertThat(off.askedAbout()).isEmpty();
    }

    @Test
    void aSourceThatFailsSendsEveryStockToTheNext() {
        FakeSource failing = FakeSource.failing("Failing");
        FakeSource next = new FakeSource("Next", true, Map.of("ABCD", "next's ABCD", "WXYZ", "next's WXYZ"));

        Map<String, String> answers = sourcesInOrder(failing, next).answersFor(List.of("ABCD", "WXYZ"), TODAY);

        assertThat(answers).containsOnlyKeys("ABCD", "WXYZ");
        assertThat(next.askedAbout()).containsExactly(List.of("ABCD", "WXYZ"));
    }

    @Test
    void aStockNoSourceKnowsIsLeftOut() {
        FakeSource first = new FakeSource("First", true, Map.of("ABCD", "first's ABCD"));
        FakeSource second = new FakeSource("Second", true, Map.of());

        Map<String, String> answers = sourcesInOrder(first, second).answersFor(List.of("ABCD", "WXYZ"), TODAY);

        assertThat(answers).containsOnlyKeys("ABCD");
    }

    @Test
    void theNextSourceIsNotAskedOnceEveryStockHasItsAnswer() {
        FakeSource first = new FakeSource("First", true, Map.of("ABCD", "first's ABCD"));
        FakeSource second = new FakeSource("Second", true, Map.of("ABCD", "second's ABCD"));

        sourcesInOrder(first, second).answersFor(List.of("ABCD"), TODAY);

        assertThat(second.askedAbout()).isEmpty();
    }

    @Test
    void theAnswersAreInTheOrderOfTheSymbolsAndAStockGivenTwiceIsAskedOnce() {
        FakeSource source = new FakeSource("Source", true, Map.of("ABCD", "ABCD's", "WXYZ", "WXYZ's"));

        Map<String, String> answers = sourcesInOrder(source).answersFor(List.of("WXYZ", "ABCD", "WXYZ"), TODAY);

        assertThat(answers.keySet()).containsExactly("WXYZ", "ABCD");
        assertThat(source.askedAbout()).containsExactly(List.of("WXYZ", "ABCD"));
    }

    @Test
    void isAnySourceConnectedOnlyWhileOneIsOn() {
        FakeSource off = new FakeSource("Off", false, Map.of());
        FakeSource on = new FakeSource("On", true, Map.of());

        assertThat(sourcesInOrder(off).isAnySourceConnected()).isFalse();
        assertThat(sourcesInOrder(off, on).isAnySourceConnected()).isTrue();
    }

    @Test
    void printsHowManyStocksEachSourceAnswered() {
        FakeSource first = new FakeSource("First", true, Map.of("ABCD", "first's ABCD"));
        FakeSource second = new FakeSource("Second", true, Map.of("WXYZ", "second's WXYZ"));

        String printed = printedWhile(() ->
                sourcesInOrder(first, second).answersFor(List.of("ABCD", "WXYZ", "NONE"), TODAY));

        assertThat(printed).isEqualTo("[sources] answers of 3 stocks: First 1, Second 1, no answer 1"
                + System.lineSeparator());
    }

    @Test
    void printsNothingWhileEverySourceIsOff() {
        FakeSource off = new FakeSource("Off", false, Map.of());

        String printed = printedWhile(() -> sourcesInOrder(off).answersFor(List.of("ABCD"), TODAY));

        assertThat(printed).isEmpty();
    }

    private SourcesInOrder<String> sourcesInOrder(FakeSource... sources) {
        return new SourcesInOrder<>("answers", List.of(sources));
    }

    private String printedWhile(Runnable action) {
        PrintStream console = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(console);
        }
        return printed.toString(StandardCharsets.UTF_8);
    }

    /**
     * A made-up source: it knows the stocks of {@code answers}, or throws on every question when {@code fails}, and
     * remembers each list of symbols it was asked about.
     */
    private record FakeSource(String sourceName, boolean isConnected, Map<String, String> answers, boolean fails,
                              List<List<String>> askedAbout) implements StockDataSource<String> {

        private FakeSource(String sourceName, boolean isConnected, Map<String, String> answers) {
            this(sourceName, isConnected, answers, false, new ArrayList<>());
        }

        private static FakeSource failing(String sourceName) {
            return new FakeSource(sourceName, true, Map.of(), true, new ArrayList<>());
        }

        @Override
        public Map<String, String> answersFor(List<String> symbols, LocalDate today) {
            askedAbout.add(List.copyOf(symbols));
            if (fails) {
                throw new IllegalStateException("no answer (Read timed out)");
            }
            return answers;
        }
    }
}
