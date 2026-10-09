package portfolioboss.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import portfolioboss.utils.Utils;

/**
 * The two API keys the stock analysis needs (AI_ANALYSIS_TODO.md, decision 12): Tavily's for the searches and
 * Anthropic's for Claude. Spring reads them from the git-ignored {@code config/local.env} (imported in
 * {@code application.properties}) or from an environment variable of the same name, which wins. A missing key turns
 * the analysis off — {@code POST /api/analysis} answers 503 with the key's name — and the rest of the app comes up as
 * usual. The keys themselves are never printed.
 */
@Component
public class AiKeys {

    private static final String TAVILY_KEY_NAME = "TAVILY_API_KEY";
    private static final String ANTHROPIC_KEY_NAME = "ANTHROPIC_API_KEY";

    private final String tavilyApiKey;
    private final String anthropicApiKey;

    /**
     * {@code ${NAME:}} is the setting of that name, or nothing when there is none — without the colon a missing key
     * would stop the whole app from starting. {@code null} for a key that is not set, or set to nothing but spaces.
     */
    protected AiKeys(@Value("${TAVILY_API_KEY:}") String tavilyApiKey,
                     @Value("${ANTHROPIC_API_KEY:}") String anthropicApiKey) {
        this.tavilyApiKey = Utils.trimmedOrNull(tavilyApiKey);
        this.anthropicApiKey = Utils.trimmedOrNull(anthropicApiKey);
    }

    public String tavilyApiKey() {
        return tavilyApiKey;
    }

    public String anthropicApiKey() {
        return anthropicApiKey;
    }

    /** The name of the first key that is not set, or {@code null} when both are. */
    public String findMissingKeyName() {
        if (tavilyApiKey == null) {
            return TAVILY_KEY_NAME;
        }
        if (anthropicApiKey == null) {
            return ANTHROPIC_KEY_NAME;
        }
        return null;
    }

    /** Spring calls this once the app is up, after the TWS sync and the UI's start. */
    @EventListener(ApplicationReadyEvent.class)
    private void printStatus() {
        String missingKeyName = findMissingKeyName();
        if (missingKeyName == null) {
            System.out.println("[ai] analysis ready");
        } else {
            System.out.println("[ai] analysis off: " + missingKeyName + " is not set (config/local.env)");
        }
    }
}
