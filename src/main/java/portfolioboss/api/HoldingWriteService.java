package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.SectorRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.TradeEntity;
import portfolioboss.db.TradeRepository;

/**
 * Stores what the user enters by hand — a holding's sector and its trades — in PortfolioBoss's own database. The
 * write-side twin of {@link PortfolioReadService}. It never creates or deletes a holding: holdings come only from the
 * sync. Each method is one transaction; the request was already validated by the controller ({@code @Valid}).
 *
 * <p>An id that does not exist is a {@code ResponseStatusException} with 404, which Spring turns into the HTTP answer
 * with this message as the {@code detail}.
 */
@Service
public class HoldingWriteService {

    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;

    public HoldingWriteService(HoldingRepository holdingRepository, TradeRepository tradeRepository) {
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional
    public void changeSector(long holdingId, SectorRequest sectorRequest) {
        HoldingEntity holding = findHolding(holdingId);
        holding.changeSector(trimmedOrNull(sectorRequest.sector()));   // Hibernate writes the change at commit
    }

    @Transactional
    public TradeResponse addTrade(long holdingId, TradeRequest tradeRequest) {
        HoldingEntity holding = findHolding(holdingId);
        TradeEntity newTrade = new TradeEntity(holding, tradeRequest.tradeDate(), tradeRequest.side(),
                tradeRequest.quantity(), tradeRequest.price(), trimmedOrNull(tradeRequest.note()));
        TradeEntity savedTrade = tradeRepository.save(newTrade);   // inserted right away, so it already has its id
        return new TradeResponse(savedTrade);
    }

    @Transactional
    public TradeResponse changeTrade(long tradeId, TradeRequest tradeRequest) {
        TradeEntity trade = findTrade(tradeId);
        trade.changeDetails(tradeRequest.tradeDate(), tradeRequest.side(), tradeRequest.quantity(),
                tradeRequest.price(), trimmedOrNull(tradeRequest.note()));
        return new TradeResponse(trade);
    }

    @Transactional
    public void deleteTrade(long tradeId) {
        tradeRepository.delete(findTrade(tradeId));
    }

    private HoldingEntity findHolding(long holdingId) {
        return holdingRepository.findById(holdingId)
                .orElseThrow(() -> notFound("No holding with id " + holdingId));
    }

    private TradeEntity findTrade(long tradeId) {
        return tradeRepository.findById(tradeId)
                .orElseThrow(() -> notFound("No trade with id " + tradeId));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    /** Surrounding spaces are dropped, and text that is then empty is stored as {@code null}: nothing was entered. */
    private String trimmedOrNull(String typedText) {
        if (typedText == null || typedText.isBlank()) {
            return null;
        }
        return typedText.strip();
    }
}
