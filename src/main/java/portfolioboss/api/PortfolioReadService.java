package portfolioboss.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.db.AccountStateEntity;
import portfolioboss.db.AccountStateRepository;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads the portfolio the last sync stored and turns it into what the API serves. The API always reads from
 * the database, never from TWS: {@code PortfolioSyncService} is the only thing that writes to it.
 */
@Service
public class PortfolioReadService {

    private final HoldingRepository holdingRepository;
    private final AccountStateRepository accountStateRepository;

    public PortfolioReadService(HoldingRepository holdingRepository, AccountStateRepository accountStateRepository) {
        this.holdingRepository = holdingRepository;
        this.accountStateRepository = accountStateRepository;
    }

    /**
     * Empty until a sync has stored something. The response is built inside the transaction: with
     * {@code open-in-view=false} that is the only place where the entities can still be read.
     */
    @Transactional(readOnly = true)
    public Optional<PortfolioResponse> currentPortfolio() {
        return accountStateRepository.findFirstByOrderByAsOfDesc().map(this::toResponse);
    }

    private PortfolioResponse toResponse(AccountStateEntity accountState) {
        List<HoldingEntity> storedHoldings = holdingRepository.findByAccountOrderById(accountState.account());
        return PortfolioResponse.from(accountState, storedHoldings);
    }
}
