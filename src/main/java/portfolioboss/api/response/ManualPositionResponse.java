package portfolioboss.api.response;

import portfolioboss.db.ManualPositionEntity;

/**
 * The answer to {@code POST /api/manual-positions}: the position just stored, with the id its trades are added to.
 * Its figures are read like everything else, from {@code closedPositions} in {@code GET /api/portfolio}.
 */
public record ManualPositionResponse(
        long id,
        String symbol,
        String currency,
        String sector,
        String note) {

    /** Public: {@code ManualPositionWriteService} answers with it. */
    public ManualPositionResponse(ManualPositionEntity manualPosition) {
        this(manualPosition.id(), manualPosition.symbol(), manualPosition.currency(), manualPosition.sector(),
                manualPosition.note());
    }
}
