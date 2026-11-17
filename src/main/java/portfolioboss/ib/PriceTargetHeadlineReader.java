package portfolioboss.ib;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one company's price targets out of its Dow Jones headlines (RECOMMENDATIONS_TODO.md, decision 14). Dow Jones
 * writes every analyst's target in one of four fixed forms:
 * <pre>
 * Snowflake Price Target Raised to $450.00/Share From $300.00 by Piper Sandler
 * APA Price Target Cut to $50.00/Share From $57.00 by Raymond James
 * Datadog Price Target Maintained With a $255.00/Share by BTIG
 * Dynatrace Price Target Announced at $54.00/Share by Wedbush
 * </pre>
 * A stock's feed also carries other companies' headlines — an article about a rival —, so a headline counts only when
 * the company it names is this one: Dow Jones's name and IB's, word by word, each of Dow Jones's words the start of IB's
 * word in the same place — "Hewlett Packard Ent" is "HEWLETT PACKARD ENTERPRISE", "Asset Entities" is not "STRIVE
 * INC-A". Anything else — another company, a columnist's "Price-Target Boost" — is not a target.
 *
 * @param companyName IB's name of the company, from its contract details: "HEWLETT PACKARD ENTERPRISE"
 */
public record PriceTargetHeadlineReader(String companyName) {

    private static final Pattern PRICE_TARGET_HEADLINE = Pattern.compile(
            "(?<company>.+?) Price Target (?:Raised to|Cut to|Maintained With a|Announced at) "
                    + "\\$(?<target>[\\d,]+(?:\\.\\d+)?)/Share"
                    + "(?: From \\$(?<previousTarget>[\\d,]+(?:\\.\\d+)?))?"
                    + " by (?<firm>.+)");
    /** IB puts its own metadata in braces before the text: "{A:800015:L:en}". */
    private static final Pattern IB_METADATA = Pattern.compile("^\\{[^}]*}");
    /** Dow Jones writes "The Trade Desk", IB "TRADE DESK INC/THE". */
    private static final String LEADING_THE = "the";

    /** The target the headline states, when it is one of this company's; empty otherwise. */
    public Optional<AnalystPriceTarget> priceTargetIn(NewsHeadline newsHeadline) {
        String text = textWithoutMetadata(newsHeadline.headline());
        Matcher match = PRICE_TARGET_HEADLINE.matcher(text);
        if (!match.matches() || !namesThisCompany(match.group("company"))) {
            return Optional.empty();
        }
        String previousTarget = match.group("previousTarget");
        return Optional.of(new AnalystPriceTarget(
                newsHeadline.publishedAt(),
                match.group("firm").trim(),
                dollarsOf(match.group("target")),
                previousTarget == null ? null : dollarsOf(previousTarget),
                text));
    }

    private String textWithoutMetadata(String headline) {
        return IB_METADATA.matcher(headline).replaceFirst("").replace("&amp;", "&").trim();
    }

    /** "1,250.00" is 1250. */
    private double dollarsOf(String amount) {
        return Double.parseDouble(amount.replace(",", ""));
    }

    private boolean namesThisCompany(String nameInHeadline) {
        List<String> headlineWords = wordsOf(nameInHeadline);
        if (!headlineWords.isEmpty() && headlineWords.getFirst().equals(LEADING_THE)) {
            headlineWords = headlineWords.subList(1, headlineWords.size());
        }
        List<String> companyWords = wordsOf(companyName);
        if (headlineWords.isEmpty() || headlineWords.size() > companyWords.size()) {
            return false;
        }
        for (int index = 0; index < headlineWords.size(); index++) {
            if (!companyWords.get(index).startsWith(headlineWords.get(index))) {
                return false;
            }
        }
        return true;
    }

    /** Lower case, split at anything that is not a letter or a digit: "INC-CL A" is "inc", "cl", "a". */
    private List<String> wordsOf(String name) {
        if (name == null) {
            return List.of();
        }
        return Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> !word.isEmpty())
                .toList();
    }
}
