package portfolioboss.exchangerates;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the {@code exchange_rate} table ({@code V17__exchange_rates.sql}): a rate the Bank of Israel published. Added
 * only by {@link ExchangeRateDownloadService}, and never changed: a published rate is final.
 */
@Entity
@Table(name = "exchange_rate")
public class ExchangeRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String currency;
    private LocalDate rateDate;
    private BigDecimal rate;

    /** Required by JPA, which creates entities by reflection. */
    protected ExchangeRateEntity() {
    }

    protected ExchangeRateEntity(String currency, BankOfIsraelRate publishedRate) {
        this.currency = currency;
        this.rateDate = publishedRate.rateDate();
        this.rate = publishedRate.rate();
    }

    protected LocalDate rateDate() {
        return rateDate;
    }

    protected BigDecimal rate() {
        return rate;
    }
}
