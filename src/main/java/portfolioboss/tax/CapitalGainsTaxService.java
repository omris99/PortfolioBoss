package portfolioboss.tax;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.calculation.tax.CapitalGainsTaxCalculator;
import portfolioboss.calculation.tax.OurFifoAgainstIbsFifo;
import portfolioboss.calculation.tax.SaleTaxableGain;
import portfolioboss.calculation.tax.SplitAdjustedFlexTrades;
import portfolioboss.calculation.tax.TaxCalculationTrade;
import portfolioboss.calculation.tax.TaxDeductibleExpense;
import portfolioboss.calculation.tax.TradesOfOneStock;
import portfolioboss.calculation.tax.TypedTradeSettlementDays;
import portfolioboss.db.investors.InvestorEntity;
import portfolioboss.db.investors.InvestorRepository;
import portfolioboss.db.portfolio.AccountStateEntity;
import portfolioboss.db.portfolio.AccountStateRepository;
import portfolioboss.db.portfolio.HoldingEntity;
import portfolioboss.db.portfolio.HoldingRepository;
import portfolioboss.db.portfolio.ManualPositionEntity;
import portfolioboss.db.portfolio.ManualPositionRepository;
import portfolioboss.db.portfolio.TradeEntity;
import portfolioboss.db.tax.ExchangeRateRepository;
import portfolioboss.db.tax.FlexCashTransactionEntity;
import portfolioboss.db.tax.FlexCashTransactionRepository;
import portfolioboss.db.tax.FlexCorporateActionEntity;
import portfolioboss.db.tax.FlexCorporateActionRepository;
import portfolioboss.db.tax.FlexTradeEntity;
import portfolioboss.db.tax.FlexTradeRepository;
import portfolioboss.exchangerates.ExchangeRates;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.flex.FlexReportTrade;
import portfolioboss.model.portfolio.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Works out the capital gains tax from what is stored (CAPITAL_GAINS_TAX_TODO.md, session 3.5): the account's from IB's
 * Flex reports — every execution, its splits followed ({@link SplitAdjustedFlexTrades}), and IB's fees as expenses —,
 * and each other investor's from the trades typed in for them, each with its Flex twin's settlement day
 * ({@link TypedTradeSettlementDays}); every amount at the Bank of Israel's rate of its settlement day. Nothing is
 * stored: the tax is derived on every call, so a trade typed in or corrected applies at once.
 *
 * <p>For now its one caller is the sync, which prints it ({@code sync.SyncService}); the Taxes page's endpoint
 * (session 4) will serve the same {@link CapitalGainsTaxOfTheAccountAndInvestors}.
 */
@Service
public class CapitalGainsTaxService {

    /** IB's type of its market data fees — the expenses that lower the account's taxable gain (decision 5). */
    private static final String OTHER_FEES = "Other Fees";

    private final AccountStateRepository accountStateRepository;
    private final HoldingRepository holdingRepository;
    private final ManualPositionRepository manualPositionRepository;
    private final InvestorRepository investorRepository;
    private final FlexTradeRepository flexTradeRepository;
    private final FlexCashTransactionRepository flexCashTransactionRepository;
    private final FlexCorporateActionRepository flexCorporateActionRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public CapitalGainsTaxService(AccountStateRepository accountStateRepository, HoldingRepository holdingRepository,
                                  ManualPositionRepository manualPositionRepository,
                                  InvestorRepository investorRepository, FlexTradeRepository flexTradeRepository,
                                  FlexCashTransactionRepository flexCashTransactionRepository,
                                  FlexCorporateActionRepository flexCorporateActionRepository,
                                  ExchangeRateRepository exchangeRateRepository) {
        this.accountStateRepository = accountStateRepository;
        this.holdingRepository = holdingRepository;
        this.manualPositionRepository = manualPositionRepository;
        this.investorRepository = investorRepository;
        this.flexTradeRepository = flexTradeRepository;
        this.flexCashTransactionRepository = flexCashTransactionRepository;
        this.flexCorporateActionRepository = flexCorporateActionRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /** The console lines of {@link CapitalGainsTaxConsoleLines}; nothing before the first sync. */
    @Transactional(readOnly = true)
    public void printCapitalGainsTax() {
        capitalGainsTaxOfTheAccountAndInvestors()
                .map(capitalGainsTax -> new CapitalGainsTaxConsoleLines(capitalGainsTax).lines())
                .ifPresent(lines -> lines.forEach(System.out::println));
    }

    /**
     * The account's tax, every other investor's and our FIFO against IB's, as of the last sync's day; empty before the
     * first sync. Read in one transaction: with {@code open-in-view=false} that is where the entities can be read.
     */
    @Transactional(readOnly = true)
    public Optional<CapitalGainsTaxOfTheAccountAndInvestors> capitalGainsTaxOfTheAccountAndInvestors() {
        return accountStateRepository.findFirstByOrderByAsOfDesc().map(this::capitalGainsTaxAsOf);
    }

    private CapitalGainsTaxOfTheAccountAndInvestors capitalGainsTaxAsOf(AccountStateEntity accountState) {
        LocalDate lastSyncDay = LocalDate.ofInstant(accountState.asOf(), ZoneId.systemDefault());
        Map<String, ExchangeRates> exchangeRatesByCurrency = Map.of(SettlementDayRatesService.US_DOLLAR,
                exchangeRateRepository.storedRatesOf(SettlementDayRatesService.US_DOLLAR));
        List<FlexReportTrade> flexTrades = flexTradeRepository.findAll().stream()
                .map(FlexTradeEntity::toFlexReportTrade)
                .toList();
        List<FlexReportCorporateAction> corporateActions = flexCorporateActionRepository.findAll().stream()
                .map(FlexCorporateActionEntity::toFlexReportCorporateAction)
                .toList();

        List<TradesOfOneStock> accountStocks =
                new SplitAdjustedFlexTrades(flexTrades, corporateActions).tradesOfEveryStock();
        CapitalGainsTaxCalculator accountTax = new CapitalGainsTaxCalculator(accountStocks, accountExpenses(),
                exchangeRatesByCurrency, lastSyncDay);
        List<InvestorCapitalGainsTax> otherInvestorsTax = otherInvestorsTax(accountState,
                new TypedTradeSettlementDays(accountStocks), exchangeRatesByCurrency, lastSyncDay);
        OurFifoAgainstIbsFifo ourFifoAgainstIbsFifo = new OurFifoAgainstIbsFifo(
                accountTax.sales().stream().map(SaleTaxableGain::fifoSale).toList(), ibsFifoGainByIbTradeId(flexTrades));
        return new CapitalGainsTaxOfTheAccountAndInvestors(accountTax, otherInvestorsTax, ourFifoAgainstIbsFifo);
    }

    /** IB's market data fees and their cancellations, in every currency IB charged them. */
    private List<TaxDeductibleExpense> accountExpenses() {
        return flexCashTransactionRepository.findByTransactionType(OTHER_FEES).stream()
                .map(FlexCashTransactionEntity::toFlexReportCashTransaction)
                .map(fee -> new TaxDeductibleExpense(fee.ibTransactionId(), fee.settleDate(), fee.currency(),
                        fee.amount(), fee.description()))
                .toList();
    }

    private Map<String, BigDecimal> ibsFifoGainByIbTradeId(List<FlexReportTrade> flexTrades) {
        Map<String, BigDecimal> ibsFifoGainByIbTradeId = new HashMap<>();
        for (FlexReportTrade flexTrade : flexTrades) {
            if (flexTrade.fifoPnlRealized() != null) {
                ibsFifoGainByIbTradeId.put(flexTrade.ibTradeId(), flexTrade.fifoPnlRealized());
            }
        }
        return ibsFifoGainByIbTradeId;
    }

    // ── each other investor: the trades typed in for them alone ─────────────────────────────────────────────

    /** In id order; the account owner is left out — their tax is what the account's leaves (decision 6). */
    private List<InvestorCapitalGainsTax> otherInvestorsTax(AccountStateEntity accountState,
                                                            TypedTradeSettlementDays typedTradeSettlementDays,
                                                            Map<String, ExchangeRates> exchangeRatesByCurrency,
                                                            LocalDate lastSyncDay) {
        List<HoldingEntity> holdings = holdingRepository.findByAccountOrderById(accountState.account());
        List<ManualPositionEntity> manualPositions = manualPositionRepository.findAllByOrderById();
        List<InvestorCapitalGainsTax> otherInvestorsTax = new ArrayList<>();
        for (InvestorEntity investor : investorRepository.findAllByOrderById()) {
            if (investor.isAccountOwner()) {
                continue;
            }
            List<TradesOfOneStock> investorsStocks =
                    investorsTradesOfEveryStock(investor.id(), holdings, manualPositions, typedTradeSettlementDays);
            otherInvestorsTax.add(new InvestorCapitalGainsTax(investor.id(), investor.name(),
                    new CapitalGainsTaxCalculator(investorsStocks, List.of(), exchangeRatesByCurrency, lastSyncDay)));
        }
        return otherInvestorsTax;
    }

    /** One stock for each holding and each manual position the investor has a trade in. */
    private List<TradesOfOneStock> investorsTradesOfEveryStock(long investorId, List<HoldingEntity> holdings,
                                                              List<ManualPositionEntity> manualPositions,
                                                              TypedTradeSettlementDays typedTradeSettlementDays) {
        List<TradesOfOneStock> investorsStocks = new ArrayList<>();
        for (HoldingEntity holding : holdings) {
            List<TaxCalculationTrade> trades = investorsTradesInOrder(investorId, holding.trades()).stream()
                    .map(trade -> taxCalculationTradeOf(trade, holding.symbol(), holding.currency(),
                            typedTradeSettlementDays.settlementDayOfAHoldingsTrade(holding.conId(), trade.tradeDate())))
                    .toList();
            if (!trades.isEmpty()) {
                investorsStocks.add(new TradesOfOneStock(holding.conId(), trades, null));
            }
        }
        for (ManualPositionEntity manualPosition : manualPositions) {
            List<TaxCalculationTrade> trades = investorsTradesInOrder(investorId, manualPosition.trades()).stream()
                    .map(trade -> taxCalculationTradeOf(trade, manualPosition.symbol(), manualPosition.currency(),
                            typedTradeSettlementDays.settlementDayOfAManualPositionsTrade(manualPosition.symbol(),
                                    trade.tradeDate())))
                    .toList();
            if (!trades.isEmpty()) {
                investorsStocks.add(new TradesOfOneStock(null, trades, null));
            }
        }
        return investorsStocks;
    }

    /**
     * As {@code calculation.portfolio.HoldingHistory} orders them: by day, a buy before a sale of the same day — a typed trade has
     * no time —, then in the order they were typed in.
     */
    private List<TradeEntity> investorsTradesInOrder(long investorId, List<TradeEntity> trades) {
        return trades.stream()
                .filter(trade -> trade.investorId() == investorId)
                .sorted(Comparator.comparing(TradeEntity::tradeDate)
                        .thenComparing(trade -> trade.side() == TradeSide.SELL)
                        .thenComparing(TradeEntity::id))
                .toList();
    }

    private TaxCalculationTrade taxCalculationTradeOf(TradeEntity trade, String symbol, String currency,
                                                      LocalDate settlementDay) {
        return new TaxCalculationTrade(String.valueOf(trade.id()), symbol, currency, trade.tradeDate(), settlementDay,
                trade.side(), trade.quantity(), trade.price(), trade.commission());
    }
}
