package portfolioboss.api.response;

import portfolioboss.db.InvestorEntity;

/**
 * The answer to {@code POST /api/investors}: the investor just stored, with the id their trades and deposits are
 * entered for. Their card is read like everything else, from {@code investors} in {@code GET /api/portfolio}.
 */
public record AddedInvestorResponse(long id, String name) {

    /** Public: {@code InvestorWriteService} answers with it. */
    public AddedInvestorResponse(InvestorEntity investor) {
        this(investor.id(), investor.name());
    }
}
