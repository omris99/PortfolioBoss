package portfolioboss.sources.finviz;

/**
 * Finviz's three screens for the Recommendations page's lists (RECOMMENDATIONS_FINVIZ_TODO.md, decisions 1 and 12): the
 * filters and the order as Finviz writes them in a screen's address — the user's own addresses, built on Finviz's site.
 * The app sorts the lists itself, so a screen without an order is in Finviz's. A threshold changes in one line here.
 */
public enum FinvizScreen {

    /**
     * Fast growth, the "dream" list: over $300M, P/E over 50, sales this quarter 30% over a year before, gross margin
     * over 50%; highest sales growth over 5 years first.
     */
    FAST_GROWTH("cap_smallover,fa_grossmargin_o50,fa_pe_o50,fa_salesqoq_o30", "-sales5years"),

    /**
     * Slow growth: over $300M, profitable by its forward P/E, P/S under 5, sales growth over 10% a year for 5 years and
     * over 5% this quarter, operating margin over 10%.
     */
    SLOW_GROWTH("cap_smallover,fa_fpe_profitable,fa_sales5years_o10,fa_salesqoq_o5,fa_opermargin_o10,fa_ps_u5", null),

    /**
     * Value: over $2B, a price over $5, forward P/E and P/FCF under 20, EPS and sales growth over 5% for 5 years,
     * operating margin over 10%, net margin over 5%.
     */
    VALUE("cap_midover,fa_eps5years_o5,fa_fpe_u20,fa_netmargin_o5,fa_opermargin_o10,fa_pfcf_u20,fa_sales5years_o5,"
            + "sh_price_o5", null);

    private final String filters;
    private final String order;

    private FinvizScreen(String filters, String order) {
        this.filters = filters;
        this.order = order;
    }

    /** "cap_smallover,fa_pe_o50": Finviz's {@code f=}. */
    public String filters() {
        return filters;
    }

    /** "-sales5years", highest first: Finviz's {@code o=}; {@code null} for Finviz's own order. */
    public String order() {
        return order;
    }
}
