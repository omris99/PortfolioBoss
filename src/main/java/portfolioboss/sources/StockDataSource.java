package portfolioboss.sources;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A source of one kind of data, asked about stocks by their symbols — one "socket" of RECOMMENDATIONS_FINVIZ_TODO.md,
 * decision 9. Each kind of data has an interface of its own that extends this one ({@link CompanyFiguresSource}), and
 * each source that can give it is a class that implements that interface, marked {@code @Order(1)}, {@code @Order(2)},
 * …: Spring hands whoever asks for a {@code List} of that interface every source in that order, and
 * {@link SourcesInOrder} asks them one after another.
 *
 * <p>{@code Answer} is what the source gives about one stock — a {@link Stock} for the company figures —, so that one
 * {@link SourcesInOrder} serves every kind of data.
 */
public interface StockDataSource<Answer> extends DataSource {

    /**
     * What the source knows about each of the stocks, by its symbol as IB writes it. A stock the source doesn't know is
     * left out of the answer, and the next source is asked about it.
     *
     * @param today the caller's day, so that the answer doesn't depend on the clock
     * @throws RuntimeException when the source can't answer at all — a refused key, no network —: every stock goes to the
     *                          next source. Its message is printed, and never holds a key.
     */
    Map<String, Answer> answersFor(List<String> symbols, LocalDate today);
}
