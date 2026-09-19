package portfolioboss.ui;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts the React UI's dev server and opens it in the browser, the way IBBot launches its
 * visualizer: {@code npm run dev} as a child process, then macOS {@code open} once it answers.
 *
 * <p>The UI is a convenience, not a dependency of the API, so every failure here is logged and
 * never stops the program.
 */
public final class UiLauncher {

    private static final int STARTUP_TIMEOUT_MILLIS = 20_000;
    private static final int POLL_INTERVAL_MILLIS = 250;
    private static final int CONNECT_TIMEOUT_MILLIS = 200;

    private final Path uiDirectory;
    private final int uiPort;

    public UiLauncher(Path uiDirectory, int uiPort) {
        this.uiDirectory = uiDirectory;
        this.uiPort = uiPort;
    }

    /** Returns immediately; the browser is opened from a background thread once the UI is up. */
    public void launch() {
        if (!Files.isDirectory(uiDirectory)) {
            System.err.println("[ui error] no ui/ directory at " + uiDirectory.toAbsolutePath() +
                    " — run from the project root");
            return;
        }

        if (isDevServerListening()) {
            System.out.println("[ui] dev server already running on port " + uiPort);
        } else if (!startDevServer()) {
            return;
        }
        openBrowserWhenReady();
    }

    private boolean startDevServer() {
        Path logFile = uiDirectory.resolve("dev-server.log");
        // A login shell, so that npm is on the PATH even when the JVM was not started from a terminal.
        ProcessBuilder command = new ProcessBuilder("bash", "-l", "-c", "npm run dev")
                .directory(uiDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());
        try {
            Process devServer = command.start();
            stopWhenJvmExits(devServer);
            System.out.println("[ui] dev server started (pid " + devServer.pid() + "), log: " + logFile);
            return true;
        } catch (IOException e) {
            System.err.println("[ui error] could not start the dev server: " + e.getMessage());
            return false;
        }
    }

    /** npm starts Vite as a child process, so stopping only npm would leave Vite holding the port. */
    private static void stopWhenJvmExits(Process devServer) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            devServer.descendants().forEach(ProcessHandle::destroy);
            devServer.destroy();
        }, "ui-dev-server-stopper"));
    }

    private void openBrowserWhenReady() {
        String url = "http://localhost:" + uiPort;
        Thread browserOpener = new Thread(() -> {
            if (waitUntilDevServerListens()) {
                openInBrowser(url);
            } else {
                System.err.println("[ui error] the UI did not come up within " +
                        STARTUP_TIMEOUT_MILLIS / 1000 + "s — is `npm install` done in ui/? See ui/dev-server.log");
            }
        }, "ui-browser-opener");
        browserOpener.setDaemon(true);
        browserOpener.start();
    }

    private boolean waitUntilDevServerListens() {
        long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (isDevServerListening()) {
                return true;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Tries every address "localhost" resolves to: Vite may listen on IPv6 ([::1]) only, where a
     * plain 127.0.0.1 probe would never succeed.
     */
    private boolean isDevServerListening() {
        InetAddress[] localhostAddresses;
        try {
            localhostAddresses = InetAddress.getAllByName("localhost");
        } catch (UnknownHostException e) {
            return false;
        }
        for (InetAddress address : localhostAddresses) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(address, uiPort), CONNECT_TIMEOUT_MILLIS);
                return true;
            } catch (IOException notListeningOnThisAddress) {
                // try the next address
            }
        }
        return false;
    }

    private static void openInBrowser(String url) {
        try {
            new ProcessBuilder("open", url).start();   // macOS, as in IBBot
            System.out.println("[ui] opened " + url);
        } catch (IOException e) {
            System.err.println("[ui error] could not open the browser — open " + url + " manually");
        }
    }
}
