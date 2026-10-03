package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.ManualClosedPositionRequest;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.db.ManualClosedPositionEntity;
import portfolioboss.db.ManualClosedPositionRepository;
import portfolioboss.utils.Utils;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Stores the closed positions the user enters by hand — round trips PortfolioBoss never saw as a holding — in
 * PortfolioBoss's own database. The twin of {@link HoldingWriteService} for the {@code manual_closed_position} table; it
 * never touches a holding or its trades. Each method is one transaction; the request was already validated by the
 * controller ({@code @Valid}), and an id that does not exist is a {@code ResponseStatusException} with 404.
 */
@Service
public class ClosedPositionWriteService {

    /** A manual row is one round trip: an order to buy and an order to sell. */
    private static final BigDecimal ORDERS_PER_ROUND_TRIP = BigDecimal.TWO;

    private final ManualClosedPositionRepository manualClosedPositionRepository;

    public ClosedPositionWriteService(ManualClosedPositionRepository manualClosedPositionRepository) {
        this.manualClosedPositionRepository = manualClosedPositionRepository;
    }

    @Transactional
    public ClosedPositionResponse addManualClosedPosition(ManualClosedPositionRequest request) {
        ManualClosedPositionEntity newRow = new ManualClosedPositionEntity(upperCaseCode(request.symbol()),
                upperCaseCode(request.currency()), Utils.trimmedOrNull(request.sector()), request.quantity(),
                request.buyDate(), request.buyPrice(), request.sellDate(), request.sellPrice(),
                commissionOrDefault(request), Utils.trimmedOrNull(request.note()));
        ManualClosedPositionEntity savedRow = manualClosedPositionRepository.save(newRow);   // inserted now: has its id
        return new ClosedPositionResponse(savedRow);
    }

    /** Every field is replaced; without a commission, the default is worked out again from the quantity now entered. */
    @Transactional
    public ClosedPositionResponse changeManualClosedPosition(long manualClosedPositionId,
                                                             ManualClosedPositionRequest request) {
        ManualClosedPositionEntity row = findManualClosedPosition(manualClosedPositionId);
        row.changeDetails(upperCaseCode(request.symbol()), upperCaseCode(request.currency()),
                Utils.trimmedOrNull(request.sector()), request.quantity(), request.buyDate(), request.buyPrice(),
                request.sellDate(), request.sellPrice(), commissionOrDefault(request),
                Utils.trimmedOrNull(request.note()));
        return new ClosedPositionResponse(row);
    }

    @Transactional
    public void deleteManualClosedPosition(long manualClosedPositionId) {
        manualClosedPositionRepository.delete(findManualClosedPosition(manualClosedPositionId));
    }

    private ManualClosedPositionEntity findManualClosedPosition(long manualClosedPositionId) {
        return manualClosedPositionRepository.findById(manualClosedPositionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No manual closed position with id " + manualClosedPositionId));
    }

    /**
     * A symbol or currency as IB writes it: " usd " is stored as "USD", so the UI's realized P&amp;L per currency doesn't
     * count it apart from "USD". Never blank here ({@code @NotBlank}).
     */
    private String upperCaseCode(String typedCode) {
        return typedCode.strip().toUpperCase(Locale.ROOT);
    }

    /** The commission entered — 0 included — or, when none was, the default for buying and then selling this many shares. */
    private BigDecimal commissionOrDefault(ManualClosedPositionRequest request) {
        if (request.commission() != null) {
            return request.commission();
        }
        return Utils.calculateOrderCommission(request.quantity()).multiply(ORDERS_PER_ROUND_TRIP);
    }
}
