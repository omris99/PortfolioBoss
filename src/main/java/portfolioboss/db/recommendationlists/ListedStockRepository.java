package portfolioboss.db.recommendationlists;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ListedStockRepository extends JpaRepository<ListedStockEntity, Long> {

    /** Every row of these symbols whose IB contract was found, in any download. */
    List<ListedStockEntity> findBySymbolInAndConIdIsNotNull(Collection<String> symbols);

    /**
     * The IB contract found for each of these symbols in an earlier download — the latest, should a symbol ever have been
     * found as two contracts. Empty for no symbol, without a query.
     */
    default Map<String, Integer> knownConIdBySymbol(Collection<String> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> conIdBySymbol = new HashMap<>();
        findBySymbolInAndConIdIsNotNull(symbols).stream()
                .sorted((first, second) -> Long.compare(first.id(), second.id()))
                .forEach(listedStock -> conIdBySymbol.put(listedStock.symbol(), listedStock.conId()));
        return conIdBySymbol;
    }
}
