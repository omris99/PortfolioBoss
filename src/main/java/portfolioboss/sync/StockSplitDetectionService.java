package portfolioboss.sync;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.calculation.splits.StockSplit;
import portfolioboss.calculation.splits.StoredStockSplits;
import portfolioboss.calculation.tax.SplitNotUnderstood;
import portfolioboss.calculation.tax.SplitsInFlexCorporateActions;
import portfolioboss.db.splits.StockSplitEntity;
import portfolioboss.db.splits.StockSplitRepository;
import portfolioboss.db.tax.FlexCorporateActionEntity;
import portfolioboss.db.tax.FlexCorporateActionRepository;
import portfolioboss.db.tax.FlexTradeEntity;
import portfolioboss.db.tax.FlexTradeRepository;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.flex.FlexReportTrade;

import java.time.Instant;
import java.util.List;

/**
 * The sync's step that stores the splits IB's Flex reports brought (STOCK_SPLITS_TODO.md, session 1.3): every split
 * understood in the stored corporate actions ({@link SplitsInFlexCorporateActions}) and not stored yet is stored, once
 * (decision 2). It reads only what is stored, so it runs after the Flex reports of every sync — and also at a startup
 * that couldn't read TWS: the first run after V20 then stores the splits already in the reports. A split not understood
 * is printed on every sync, since it is checked again on every one.
 */
@Service
public class StockSplitDetectionService {

    private final FlexCorporateActionRepository flexCorporateActionRepository;
    private final FlexTradeRepository flexTradeRepository;
    private final StockSplitRepository stockSplitRepository;

    public StockSplitDetectionService(FlexCorporateActionRepository flexCorporateActionRepository,
                                      FlexTradeRepository flexTradeRepository,
                                      StockSplitRepository stockSplitRepository) {
        this.flexCorporateActionRepository = flexCorporateActionRepository;
        this.flexTradeRepository = flexTradeRepository;
        this.stockSplitRepository = stockSplitRepository;
    }

    /** Stores each new split and prints it; prints each split not understood. Nothing when IB reported no action. */
    @Transactional
    public void storeNewSplits() {
        List<FlexReportCorporateAction> corporateActions = flexCorporateActionRepository.findAll().stream()
                .map(FlexCorporateActionEntity::toFlexReportCorporateAction)
                .toList();
        if (corporateActions.isEmpty()) {
            return;
        }
        List<FlexReportTrade> flexTrades = flexTradeRepository.findAll().stream()
                .map(FlexTradeEntity::toFlexReportTrade)
                .toList();
        SplitsInFlexCorporateActions splitsInIbsReports = new SplitsInFlexCorporateActions(corporateActions, flexTrades);
        StoredStockSplits storedSplits = stockSplitRepository.storedStockSplits();

        Instant storedAt = Instant.now();
        for (StockSplit split : splitsInIbsReports.splitsUnderstood()) {
            if (!storedSplits.isStored(split.ibActionId())) {
                stockSplitRepository.save(new StockSplitEntity(split, storedAt));
                System.out.println("[db] stored a stock split: " + consoleWordsOf(split));
            }
        }
        for (SplitNotUnderstood splitNotUnderstood : splitsInIbsReports.splitsNotUnderstood()) {
            System.out.println("[db] a stock split not understood: " + splitNotUnderstood.symbol() + " on "
                    + splitNotUnderstood.actionTime().toLocalDate() + ": " + splitNotUnderstood.reason().description());
        }
    }

    /** "ABCD 4 for 1 on 2026-03-10", and ", into a new contract" when it moved the stock. */
    private String consoleWordsOf(StockSplit split) {
        String words = split.symbol() + " " + split.ratioInIbsWords() + " on " + split.splitDay();
        return split.keepsTheSameContract() ? words : words + ", into a new contract";
    }
}
