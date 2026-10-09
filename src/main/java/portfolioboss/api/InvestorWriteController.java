package portfolioboss.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.request.CashMovementRequest;
import portfolioboss.api.request.InvestorRequest;
import portfolioboss.api.response.AddedInvestorResponse;
import portfolioboss.api.response.CashMovementResponse;

/**
 * The endpoints that write the investors and their deposits and withdrawals to PortfolioBoss's own database. Like
 * {@link HoldingWriteController} they never reach Interactive Brokers, accept JSON only ({@code consumes}, for the same
 * reason: a page on another site can't send JSON to localhost without a CORS permission nobody grants), and are checked
 * by {@code @Valid} before the method runs. After a write the UI reloads {@code /api/portfolio}, which holds the cards.
 */
@RestController
class InvestorWriteController {

    private final InvestorWriteService investorWriteService;

    private InvestorWriteController(InvestorWriteService investorWriteService) {
        this.investorWriteService = investorWriteService;
    }

    @PostMapping(path = "/api/investors", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<AddedInvestorResponse> addInvestor(@Valid @RequestBody InvestorRequest request) {
        AddedInvestorResponse addedInvestor = investorWriteService.addInvestor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(addedInvestor);
    }

    @PutMapping(path = "/api/investors/{investorId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<Void> renameInvestor(@PathVariable long investorId,
                                                @Valid @RequestBody InvestorRequest request) {
        investorWriteService.renameInvestor(investorId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/api/investors/{investorId}/cash-movements", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<CashMovementResponse> addCashMovement(@PathVariable long investorId,
                                                                 @Valid @RequestBody CashMovementRequest request) {
        CashMovementResponse addedMovement = investorWriteService.addCashMovement(investorId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(addedMovement);
    }

    @PutMapping(path = "/api/cash-movements/{movementId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<CashMovementResponse> changeCashMovement(@PathVariable long movementId,
                                                                    @Valid @RequestBody CashMovementRequest request) {
        return ResponseEntity.ok(investorWriteService.changeCashMovement(movementId, request));
    }

    @DeleteMapping("/api/cash-movements/{movementId}")
    private ResponseEntity<Void> deleteCashMovement(@PathVariable long movementId) {
        investorWriteService.deleteCashMovement(movementId);
        return ResponseEntity.noContent().build();
    }
}
