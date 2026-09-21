package portfolioboss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PortfolioBoss — Milestone 0 walking skeleton, a Spring Boot application.
 *
 * <p>Connects read-only to a running IB TWS/Gateway, reads the account's real holdings (quantity
 * and average cost come straight from the broker), prints them, disconnects, and then serves that
 * snapshot to the UI on localhost ({@code GET /api/portfolio}) until stopped, starting the UI and
 * opening it in the browser. This proves the one IB call the long-term portfolio platform is built
 * on: {@code reqAccountUpdates}. It never trades.
 *
 * <p>Spring Boot brings the web server up (its address and port are in {@code application.properties});
 * {@link TwsPortfolioRunner} then does the TWS read.
 *
 * <pre>
 *   ./run.sh                 # 127.0.0.1:7496, clientId 101
 *   ./run.sh 7497            # paper-trading port
 *   ./run.sh 7496 102        # custom clientId
 * </pre>
 */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        System.out.println(AppMetadata.getSignature() + " · read-only portfolio reader");
        SpringApplication.run(Main.class, args);
    }
}
