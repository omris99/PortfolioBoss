package portfolioboss.sources;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sources of one kind of data, asked one after another in their order (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9):
 * the first that answers for a stock gives its answer. A source that isn't connected is skipped without a word; one that
 * fails prints why, and the next one is asked about every stock still without an answer — as it is about a stock the
 * source before it doesn't know. A whole stock moves to the next source, never a single figure, so that each stock's
 * answer is one source's.
 *
 * <p>Built by whoever needs the data, from the {@code List} Spring hands it — every source of that kind, in the order of
 * their {@code @Order}. {@code Answer} is what one source gives about one stock.
 *
 * @param kindOfData     what the sources give, for the console: "company figures"
 * @param sourcesInOrder the sources, the one to ask first first
 */
public record SourcesInOrder<Answer>(String kindOfData, List<? extends StockDataSource<Answer>> sourcesInOrder) {

    /** {@code false} when every source is off: nothing would be asked. */
    public boolean isAnySourceConnected() {
        return sourcesInOrder.stream().anyMatch(DataSource::isConnected);
    }

    /**
     * Each stock's answer, by symbol, in the order of {@code symbols}; a stock no source answered for is left out. Never
     * throws. When a source is connected, prints who answered, for example "[sources] company figures of 10 stocks:
     * Finviz 7, Finnhub 2, no answer 1".
     *
     * @param today the caller's day, handed to every source
     */
    public Map<String, Answer> answersFor(List<String> symbols, LocalDate today) {
        List<String> stillWithoutAnAnswer = symbols.stream().distinct().toList();
        int stockCount = stillWithoutAnAnswer.size();
        Map<String, Answer> answersBySymbol = new HashMap<>();
        Map<String, Integer> answerCountBySourceName = new LinkedHashMap<>();
        for (StockDataSource<Answer> source : sourcesInOrder) {
            if (stillWithoutAnAnswer.isEmpty()) {
                break;
            }
            if (!source.isConnected()) {
                continue;
            }
            Map<String, Answer> answersOfTheSource = answersOf(source, stillWithoutAnAnswer, today);
            List<String> answeredNow = stillWithoutAnAnswer.stream()
                    .filter(symbol -> answersOfTheSource.get(symbol) != null)
                    .toList();
            answeredNow.forEach(symbol -> answersBySymbol.put(symbol, answersOfTheSource.get(symbol)));
            answerCountBySourceName.put(source.sourceName(), answeredNow.size());
            stillWithoutAnAnswer = stillWithoutAnAnswer.stream()
                    .filter(symbol -> !answersBySymbol.containsKey(symbol))
                    .toList();
        }
        if (!answerCountBySourceName.isEmpty()) {
            printWhoAnswered(stockCount, answerCountBySourceName, stillWithoutAnAnswer.size());
        }
        return inTheOrderOf(symbols, answersBySymbol);
    }

    /** A source that throws gives nothing: every stock it was asked about goes to the next source. */
    private Map<String, Answer> answersOf(StockDataSource<Answer> source, List<String> symbols, LocalDate today) {
        try {
            Map<String, Answer> answers = source.answersFor(symbols, today);
            return answers == null ? Map.of() : answers;
        } catch (RuntimeException e) {
            System.err.println("[sources error] " + source.sourceName() + " could not give the " + kindOfData + ": "
                    + e.getMessage());
            return Map.of();
        }
    }

    private void printWhoAnswered(int stockCount, Map<String, Integer> answerCountBySourceName, int unansweredCount) {
        List<String> counts = new ArrayList<>();
        answerCountBySourceName.forEach((sourceName, answerCount) -> counts.add(sourceName + " " + answerCount));
        if (unansweredCount > 0) {
            counts.add("no answer " + unansweredCount);
        }
        System.out.println("[sources] " + kindOfData + " of " + stockCount + " stocks: " + String.join(", ", counts));
    }

    private Map<String, Answer> inTheOrderOf(List<String> symbols, Map<String, Answer> answersBySymbol) {
        Map<String, Answer> answersInOrder = new LinkedHashMap<>();
        symbols.stream()
                .filter(answersBySymbol::containsKey)
                .forEach(symbol -> answersInOrder.put(symbol, answersBySymbol.get(symbol)));
        return answersInOrder;
    }
}
