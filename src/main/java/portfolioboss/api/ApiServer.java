package portfolioboss.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import portfolioboss.model.PortfolioSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * The local HTTP API the UI reads from: serves one portfolio snapshot as JSON.
 *
 * <p>Read-only by construction — a single GET endpoint, bound to the loopback interface so the
 * account data is never reachable from another machine.
 */
public final class ApiServer {

    private final HttpServer server;
    private final byte[] portfolioJson;

    public ApiServer(int port, PortfolioSnapshot snapshot) throws IOException {
        this.portfolioJson = PortfolioJson.toJson(snapshot).getBytes(StandardCharsets.UTF_8);
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api/portfolio", this::handlePortfolio);
    }

    public void start() {
        server.start();
    }

    private void handlePortfolio(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, portfolioJson.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(portfolioJson);
            }
        }
    }
}
