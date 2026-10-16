package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.ManualPositionRequest;
import portfolioboss.api.request.NewManualPositionRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.ManualPositionResponse;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.calculation.OrderCommission;
import portfolioboss.db.InvestorEntity;
import portfolioboss.db.ManualPositionEntity;
import portfolioboss.db.ManualPositionRepository;
import portfolioboss.db.TradeEntity;
import portfolioboss.db.TradeRepository;
import portfolioboss.model.TradeSide;
import portfolioboss.utils.Utils;

import java.util.Locale;

/**
 * Stores the manual positions the user enters by hand — positions PortfolioBoss never saw as a holding, sold before the
 * first sync — with their buys and sells, in PortfolioBoss's own database. It never touches a holding. Correcting or
 * deleting one of their trades goes through {@link HoldingWriteService}, like any trade. Each method is one
 * transaction; the request was already validated by the controller ({@code @Valid}), and an id that does not exist is
 * a {@code ResponseStatusException} with 404.
 */
@Service
public class ManualPositionWriteService {

    private final ManualPositionRepository manualPositionRepository;
    private final TradeRepository tradeRepository;
    private final InvestorWriteService investorWriteService;

    public ManualPositionWriteService(ManualPositionRepository manualPositionRepository,
                                      TradeRepository tradeRepository, InvestorWriteService investorWriteService) {
        this.manualPositionRepository = manualPositionRepository;
        this.tradeRepository = tradeRepository;
        this.investorWriteService = investorWriteService;
    }

    /**
     * The position, then its first buy and first sell; a commission left empty is the default for that order. Both
     * trades are the investor's the request names, or the account owner's when it names none.
     */
    @Transactional
    public ManualPositionResponse addManualPosition(NewManualPositionRequest request) {
        InvestorEntity investor = investorWriteService.investorOfTrade(request.investorId());   // 400 before any write
        ManualPositionEntity newPosition = manualPositionRepository.save(new ManualPositionEntity(
                upperCaseCode(request.symbol()), upperCaseCode(request.currency()),
                Utils.trimmedOrNull(request.sector()), Utils.trimmedOrNull(request.note())));
        tradeRepository.save(new TradeEntity(newPosition, investor, request.buyDate(), TradeSide.BUY,
                request.quantity(), request.buyPrice(), null,
                OrderCommission.orDefault(request.buyCommission(), request.quantity())));
        tradeRepository.save(new TradeEntity(newPosition, investor, request.sellDate(), TradeSide.SELL,
                request.quantity(), request.sellPrice(), null,
                OrderCommission.orDefault(request.sellCommission(), request.quantity())));
        return new ManualPositionResponse(newPosition);
    }

    /** Its own details only; its trades stay as they are. */
    @Transactional
    public void changeManualPosition(long manualPositionId, ManualPositionRequest request) {
        findManualPosition(manualPositionId).changeDetails(upperCaseCode(request.symbol()),
                upperCaseCode(request.currency()), Utils.trimmedOrNull(request.sector()),
                Utils.trimmedOrNull(request.note()));
    }

    /** Its trades go with it: they belong to nothing else. */
    @Transactional
    public void deleteManualPosition(long manualPositionId) {
        ManualPositionEntity manualPosition = findManualPosition(manualPositionId);
        tradeRepository.deleteAll(manualPosition.trades());
        manualPositionRepository.delete(manualPosition);
    }

    /** The investor the request names, or the account owner — like {@link HoldingWriteService#addTrade}. */
    @Transactional
    public TradeResponse addTrade(long manualPositionId, TradeRequest tradeRequest) {
        ManualPositionEntity manualPosition = findManualPosition(manualPositionId);
        InvestorEntity investor = investorWriteService.investorOfTrade(tradeRequest.investorId());
        TradeEntity newTrade = new TradeEntity(manualPosition, investor, tradeRequest.tradeDate(), tradeRequest.side(),
                tradeRequest.quantity(), tradeRequest.price(), Utils.trimmedOrNull(tradeRequest.note()),
                OrderCommission.orDefault(tradeRequest.commission(), tradeRequest.quantity()));
        return new TradeResponse(tradeRepository.save(newTrade));
    }

    private ManualPositionEntity findManualPosition(long manualPositionId) {
        return manualPositionRepository.findById(manualPositionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No manual position with id " + manualPositionId));
    }

    /**
     * A symbol or currency as IB writes it: " usd " is stored as "USD", so the UI's realized P&amp;L per currency doesn't
     * count it apart from "USD". Never blank here ({@code @NotBlank}).
     */
    private String upperCaseCode(String typedCode) {
        return typedCode.strip().toUpperCase(Locale.ROOT);
    }
}
