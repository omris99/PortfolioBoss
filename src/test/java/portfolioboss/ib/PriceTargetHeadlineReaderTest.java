package portfolioboss.ib;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads price targets out of Dow Jones headlines as IB sends them — the four forms, each copied from the real feed of
 * 2026-10-29 — and leaves out what isn't this company's target.
 */
class PriceTargetHeadlineReaderTest {

    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 9, 3, 17, 40);

    @Test
    void readsARaisedTargetWithTheOneItReplaced() {
        Optional<AnalystPriceTarget> priceTarget = new PriceTargetHeadlineReader("SNOWFLAKE INC")
                .priceTargetIn(headline("{A:800015:L:en}Snowflake Price Target Raised to $450.00/Share From $320.00 "
                        + "by Piper Sandler"));

        assertThat(priceTarget).contains(new AnalystPriceTarget(PUBLISHED_AT, "Piper Sandler", 450.0, 320.0,
                "Snowflake Price Target Raised to $450.00/Share From $320.00 by Piper Sandler"));
    }

    @Test
    void readsACutTarget() {
        AnalystPriceTarget priceTarget = new PriceTargetHeadlineReader("APA CORP")
                .priceTargetIn(headline("{A:800015:L:en}APA Price Target Cut to $50.00/Share From $57.00 by Raymond James"))
                .orElseThrow();

        assertThat(priceTarget.priceTarget()).isEqualTo(50.0);
        assertThat(priceTarget.previousPriceTarget()).isEqualTo(57.0);
        assertThat(priceTarget.firm()).isEqualTo("Raymond James");
    }

    /** "Maintained With a" and "Announced at" name no earlier target. */
    @Test
    void readsAMaintainedAndAnAnnouncedTargetWithoutAnEarlierOne() {
        AnalystPriceTarget maintained = new PriceTargetHeadlineReader("DATADOG INC - CLASS A")
                .priceTargetIn(headline("{A:800015:L:en}Datadog Price Target Maintained With a $255.00/Share by BTIG"))
                .orElseThrow();
        AnalystPriceTarget announced = new PriceTargetHeadlineReader("DYNATRACE INC")
                .priceTargetIn(headline("{A:800015:L:en}Dynatrace Price Target Announced at $54.00/Share by Wedbush"))
                .orElseThrow();

        assertThat(maintained.priceTarget()).isEqualTo(255.0);
        assertThat(maintained.previousPriceTarget()).isNull();
        assertThat(announced.priceTarget()).isEqualTo(54.0);
        assertThat(announced.firm()).isEqualTo("Wedbush");
    }

    @Test
    void readsAThousandsSeparatorAndAnAmpersand() {
        AnalystPriceTarget priceTarget = new PriceTargetHeadlineReader("STRIVE INC-A")
                .priceTargetIn(headline("Strive Price Target Raised to $1,250.00/Share From $1,100.00 "
                        + "by HC Wainwright &amp; Co."))
                .orElseThrow();

        assertThat(priceTarget.priceTarget()).isEqualTo(1250.0);
        assertThat(priceTarget.previousPriceTarget()).isEqualTo(1100.0);
        assertThat(priceTarget.firm()).isEqualTo("HC Wainwright & Co.");
    }

    /** Dow Jones shortens the last word, and writes "The" where IB puts it at the end. */
    @Test
    void matchesTheCompanyByTheStartOfEachWordOfIbsName() {
        assertThat(new PriceTargetHeadlineReader("HEWLETT PACKARD ENTERPRISE")
                .priceTargetIn(headline("Hewlett Packard Ent Price Target Raised to $28.00/Share From $25.00 by Citigroup")))
                .isPresent();
        assertThat(new PriceTargetHeadlineReader("TRADE DESK INC/THE -CLASS A")
                .priceTargetIn(headline("The Trade Desk Price Target Cut to $60.00/Share From $70.00 by Baird")))
                .isPresent();
    }

    /** ASST's feed carried targets of Asset Entities, the company Strive merged with. */
    @Test
    void leavesOutAnotherCompanysTargetInTheFeed() {
        assertThat(new PriceTargetHeadlineReader("STRIVE INC-A")
                .priceTargetIn(headline("Asset Entities Price Target Raised to $8.00/Share From $6.00 by Maxim Group")))
                .isEmpty();
        assertThat(new PriceTargetHeadlineReader("APPLIED MATERIALS INC")
                .priceTargetIn(headline("Apple Price Target Raised to $300.00/Share From $280.00 by Wedbush")))
                .isEmpty();
    }

    @Test
    void leavesOutHeadlinesThatAreNotATarget() {
        PriceTargetHeadlineReader palantir = new PriceTargetHeadlineReader("PALANTIR TECHNOLOGIES INC-A");

        assertThat(palantir.priceTargetIn(headline(
                "Palantir Stock Gets Price-Target Boost. Cheaper AI Could Fuel More Growth. -- Barrons.com"))).isEmpty();
        assertThat(palantir.priceTargetIn(headline("Palantir Technologies Is Maintained at Buy by Mizuho"))).isEmpty();
        assertThat(palantir.priceTargetIn(headline(
                "Dow Jones Futures Rise; SpaceX IPO Price Target Set -- IBD"))).isEmpty();
    }

    private NewsHeadline headline(String text) {
        return new NewsHeadline(PUBLISHED_AT, "DJ-N$1", text);
    }
}
