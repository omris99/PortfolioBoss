package portfolioboss.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.regex.Pattern;

/**
 * One search result as Tavily returns it — read straight from Tavily's JSON, so the names are Tavily's. Untrusted text
 * from the web: it goes to Claude only as data (AI_ANALYSIS_TODO.md, decision 13).
 *
 * @param publishedDate as Tavily writes it; {@code null} when it doesn't know
 * @param content       the parts of the page that match the search, not the whole page (decision 3)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResult(
        String title,
        String url,
        @JsonProperty("published_date") String publishedDate,
        String content) {

    /**
     * The symbol as a whole word in any case, in the title or the text: "AEVA", "$AEVA" and "Aeva Technologies" count,
     * "AEVAX" doesn't.
     */
    public boolean mentions(String symbol) {
        Pattern wholeWord = Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b", Pattern.CASE_INSENSITIVE);
        return containsWord(title, wholeWord) || containsWord(content, wholeWord);
    }

    private boolean containsWord(String text, Pattern wholeWord) {
        return text != null && wholeWord.matcher(text).find();
    }
}
