package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.SectorRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.calculation.OrderCommission;
import portfolioboss.calculation.TradeSide;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.InvestorEntity;
import portfolioboss.db.ManualPositionEntity;
import portfolioboss.db.TradeEntity;
import portfolioboss.db.TradeRepository;
import portfolioboss.utils.Utils;

/**
 * Stores what the user enters by hand — a holding's sector and its trades — in PortfolioBoss's own database, and
 * corrects or deletes any trade, a manual position's too ({@link ManualPositionWriteService} adds those). The
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
    private final InvestorWriteService investorWriteService;

    public HoldingWriteService(HoldingRepository holdingRepository, TradeRepository tradeRepository,
                               InvestorWriteService investorWriteService) {
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.investorWriteService = investorWriteService;
    }

    @Transactional
    public void changeSector(long holdingId, SectorRequest sectorRequest) {
        HoldingEntity holding = findHolding(holdingId);
        holding.changeSector(Utils.trimmedOrNull(sectorRequest.sector()));   // Hibernate writes the change at commit
    }

    /** The investor the request names, or the account owner when it names none. */
    @Transactional
    public TradeResponse addTrade(long holdingId, TradeRequest tradeRequest) {
        HoldingEntity holding = findHolding(holdingId);
        InvestorEntity investor = investorWriteService.investorOfTrade(tradeRequest.investorId());
        TradeEntity newTrade = new TradeEntity(holding, investor, tradeRequest.tradeDate(), tradeRequest.side(),
                tradeRequest.quantity(), tradeRequest.price(), Utils.trimmedOrNull(tradeRequest.note()),
                OrderCommission.orDefault(tradeRequest.commission(), tradeRequest.quantity()));
        TradeEntity savedTrade = tradeRepository.save(newTrade);   // inserted right away, so it already has its id
        return new TradeResponse(savedTrade);
    }

    /**
     * Any trade, a holding's or a manual position's. Without a commission, the default is worked out again from the
     * quantity now entered; without an investor, the trade stays the one it had. The last sell of a manual position
     * may be corrected, but not turned into a buy.
     */
    @Transactional
    public TradeResponse changeTrade(long tradeId, TradeRequest tradeRequest) {
        TradeEntity trade = findTrade(tradeId);
        if (tradeRequest.side() == TradeSide.BUY) {
            refuseToRemoveTheLastSellOfAManualPosition(trade);
        }
        trade.changeDetails(tradeRequest.tradeDate(), tradeRequest.side(), tradeRequest.quantity(),
                tradeRequest.price(), Utils.trimmedOrNull(tradeRequest.note()),
                OrderCommission.orDefault(tradeRequest.commission(), tradeRequest.quantity()));
        if (tradeRequest.investorId() != null) {
            trade.changeInvestor(investorWriteService.investorOfTrade(tradeRequest.investorId()));
        }
        return new TradeResponse(trade);
    }

    /** Any trade, a holding's or a manual position's — except the last sell of a manual position. */
    @Transactional
    public void deleteTrade(long tradeId) {
        TradeEntity trade = findTrade(tradeId);
        refuseToRemoveTheLastSellOfAManualPosition(trade);
        tradeRepository.delete(trade);
    }

    /**
     * A manual position shows up only through its sells: without one it would vanish from the closed positions, trades
     * and all. So its last sell can be corrected but not deleted or turned into a buy — the whole position is deleted
     * instead. 409 Conflict: the request is valid, but not for the position as it stands.
     */
    private void refuseToRemoveTheLastSellOfAManualPosition(TradeEntity trade) {
        ManualPositionEntity manualPosition = trade.manualPosition();
        if (manualPosition == null || trade.side() != TradeSide.SELL) {
            return;
        }
        long sellCount = manualPosition.trades().stream()
                .filter(positionTrade -> positionTrade.side() == TradeSide.SELL)
                .count();
        if (sellCount <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This is the last sell of a manual position: it "
                    + "can be corrected, but not deleted or turned into a buy. Delete the whole position instead.");
        }
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
}
