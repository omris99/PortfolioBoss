package portfolioboss.exchangerates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Reads and writes {@link ExchangeRateEntity} rows; Spring generates the class at startup, like the other repositories. */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, Long> {

    List<ExchangeRateEntity> findByCurrency(String currency);

    /** Every stored rate of the currency, in one query, with the rule of which rate a settlement day takes. */
    default ExchangeRates storedRatesOf(String currency) {
        NavigableMap<LocalDate, BigDecimal> ratesByDate = new TreeMap<>();
        for (ExchangeRateEntity storedRate : findByCurrency(currency)) {
            ratesByDate.put(storedRate.rateDate(), storedRate.rate());
        }
        return new ExchangeRates(currency, ratesByDate);
    }
}
