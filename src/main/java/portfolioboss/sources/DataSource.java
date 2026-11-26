package portfolioboss.sources;

/**
 * What every source of data from outside the app says about itself (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9): its
 * name, as the screens and the console write it, and whether it can be asked at all. A source without its key is off,
 * and the app works on without it.
 */
public interface DataSource {

    /** "Finviz", "Finnhub": the name the screens show beside its answer ("From Finviz, answered …"). */
    String sourceName();

    /** {@code false} while its key is not set: it is skipped without a word, and the next source is asked. */
    boolean isConnected();
}
