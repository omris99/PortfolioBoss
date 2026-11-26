package portfolioboss.sources.finviz;

import org.springframework.stereotype.Component;
import portfolioboss.exceptions.FinvizUnavailableException;

import java.time.LocalDate;
import java.util.List;

/**
 * Downloads one of the three screens of the Recommendations page's lists (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9,
 * option א of 26.11: the lists have no socket — only Finviz can give them —, and {@code recommendations} turns these
 * rows into the app's own in session 2). Nothing calls it yet.
 */
@Component
public class FinvizScreens {

    private final FinvizClient finvizClient;

    protected FinvizScreens(FinvizClient finvizClient) {
        this.finvizClient = finvizClient;
    }

    /** {@code false} without the token: the page then says "Finviz is not connected" (decision 10). */
    public boolean isConnected() {
        return finvizClient.hasToken();
    }

    /**
     * Every stock that passes the screen today, in the screen's order.
     *
     * @param today the day of the download, for a date Finviz writes without its year
     * @throws FinvizUnavailableException when Finviz refuses, doesn't answer, or its answer can't be read
     */
    public List<FinvizStock> stocksOf(FinvizScreen screen, LocalDate today) {
        String csv = finvizClient.screenAsCsv(screen.filters(), screen.order(), List.of());
        return new FinvizScreenerReader(today).stocksIn(csv);
    }
}
