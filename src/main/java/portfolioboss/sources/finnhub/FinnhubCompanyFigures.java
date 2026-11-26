package portfolioboss.sources.finnhub;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import portfolioboss.sources.CompanyFiguresSource;
import portfolioboss.sources.Stock;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Finnhub as the second source of the company figures (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9): asked about the
 * stocks Finviz didn't answer for, or about every stock while Finviz isn't connected. Finnhub answers one stock at a time
 * (two calls each), so the stocks are asked all at once, each on a virtual thread of its own.
 */
@Component
@Order(2)
public class FinnhubCompanyFigures implements CompanyFiguresSource {

    private final FinnhubClient finnhubClient;

    protected FinnhubCompanyFigures(FinnhubClient finnhubClient) {
        this.finnhubClient = finnhubClient;
    }

    @Override
    public String sourceName() {
        return FinnhubClient.SOURCE_NAME;
    }

    @Override
    public boolean isConnected() {
        return finnhubClient.hasApiKey();
    }

    /**
     * A stock whose call failed prints {@code [finnhub error] SYMBOL: why} and is left out, so that the next source is
     * asked about it — the others' answers stay.
     */
    @Override
    public Map<String, Stock> answersFor(List<String> symbols, LocalDate today) {
        Map<String, Future<Stock>> pendingAnswersBySymbol = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String symbol : symbols) {
                pendingAnswersBySymbol.put(symbol,
                        executor.submit(() -> finnhubClient.stockOrNullOnFailure(symbol, today)));
            }
        }   // closing the executor waits for every call to end

        Map<String, Stock> answersBySymbol = new LinkedHashMap<>();
        pendingAnswersBySymbol.forEach((symbol, pendingAnswer) -> {
            Stock answer = pendingAnswer.resultNow();
            if (answer != null) {
                answersBySymbol.put(symbol, answer);
            }
        });
        return answersBySymbol;
    }
}
