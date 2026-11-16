package portfolioboss.tax;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.tax.request.TaxConfirmationCorrectionRequest;
import portfolioboss.tax.request.TaxConfirmationsRequest;
import portfolioboss.tax.response.TaxConfirmationResponse;
import portfolioboss.tax.response.TaxesResponse;

import java.util.List;
import java.util.Optional;

/**
 * The Taxes page's endpoints (CAPITAL_GAINS_TAX_TODO.md, session 4), all under {@code /api/taxes}: the tax, and the
 * confirmations of what Phoenix recorded — confirm, correct, cancel. The writes touch only PortfolioBoss's own
 * {@code tax_confirmation} table, and accept JSON only ({@code consumes}), like every other write: a page on another site
 * could otherwise send them with a plain form.
 */
@RestController
class TaxController {

    private final TaxesReadService taxesReadService;
    private final TaxConfirmationService taxConfirmationService;

    private TaxController(TaxesReadService taxesReadService, TaxConfirmationService taxConfirmationService) {
        this.taxesReadService = taxesReadService;
        this.taxConfirmationService = taxConfirmationService;
    }

    /** {@code no-store}, like {@code /api/portfolio}; 503 before the first sync, as there. */
    @GetMapping("/api/taxes")
    private ResponseEntity<TaxesResponse> taxes() {
        Optional<TaxesResponse> taxes = taxesReadService.taxes();
        if (taxes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(taxes.get());
    }

    /** One day, or a whole month's days still waiting: 201 with each new confirmation and its id. */
    @PostMapping(path = "/api/taxes/confirmations", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<List<TaxConfirmationResponse>> confirm(@Valid @RequestBody TaxConfirmationsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taxConfirmationService.confirm(request));
    }

    @PutMapping(path = "/api/taxes/confirmations/{confirmationId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<Void> correct(@PathVariable long confirmationId,
                                         @Valid @RequestBody TaxConfirmationCorrectionRequest request) {
        taxConfirmationService.correct(confirmationId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/taxes/confirmations/{confirmationId}")
    private ResponseEntity<Void> cancel(@PathVariable long confirmationId) {
        taxConfirmationService.cancel(confirmationId);
        return ResponseEntity.noContent().build();
    }
}
