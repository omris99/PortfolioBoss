package portfolioboss.tax;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.tax.response.InvestorTaxResponse;
import portfolioboss.tax.response.MonthlySettlementResponse;
import portfolioboss.tax.response.SaleResponse;
import portfolioboss.tax.response.SettlementDayResponse;
import portfolioboss.tax.response.SharesSoldFromOneBuyResponse;
import portfolioboss.tax.response.TaxConfirmationResponse;
import portfolioboss.tax.response.TaxDeductibleExpenseResponse;
import portfolioboss.tax.response.TaxYearResponse;
import portfolioboss.tax.response.TaxesResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Puts {@code GET /api/taxes} together (CAPITAL_GAINS_TAX_TODO.md, session 4): the tax {@link CapitalGainsTaxService}
 * works out, and the confirmations stored — the account's years as recorded ({@link AccountTaxWithItsConfirmations}),
 * every other investor's as calculated —, and (session 5) whether IB's Flex reports are connected and how far they are
 * stored. The tax's records are internal to this package, so the response records are built here, in private methods, as
 * {@code PortfolioReadService} builds the portfolio's.
 */
@Service
public class TaxesReadService {

    private final CapitalGainsTaxService capitalGainsTaxService;
    private final TaxConfirmationRepository taxConfirmationRepository;
    private final FlexClient flexClient;
    private final FlexReportStorageService flexReportStorageService;

    public TaxesReadService(CapitalGainsTaxService capitalGainsTaxService,
                            TaxConfirmationRepository taxConfirmationRepository, FlexClient flexClient,
                            FlexReportStorageService flexReportStorageService) {
        this.capitalGainsTaxService = capitalGainsTaxService;
        this.taxConfirmationRepository = taxConfirmationRepository;
        this.flexClient = flexClient;
        this.flexReportStorageService = flexReportStorageService;
    }

    /** Empty before the first sync. The tax and the confirmations are read in one transaction, so they always agree. */
    @Transactional(readOnly = true)
    public Optional<TaxesResponse> taxes() {
        return capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors().map(this::taxesResponseOf);
    }

    private TaxesResponse taxesResponseOf(CapitalGainsTaxOfTheAccountAndInvestors capitalGainsTax) {
        List<TaxConfirmation> confirmations = taxConfirmationRepository.findAllByOrderBySettlementDay().stream()
                .map(TaxConfirmationEntity::toTaxConfirmation)
                .toList();
        List<TaxYearResponse> accountTaxYears =
                new AccountTaxWithItsConfirmations(capitalGainsTax.accountTax(), confirmations).taxYears().stream()
                        .map(this::accountTaxYearResponseOf)
                        .toList();
        List<InvestorTaxResponse> otherInvestorsTax = capitalGainsTax.otherInvestorsTax().stream()
                .map(this::investorTaxResponseOf)
                .toList();
        return new TaxesResponse(capitalGainsTax.accountTax().lastSyncDay(), accountTaxYears, otherInvestorsTax,
                flexClient.hasKeys(), flexReportStorageService.lastStoredDate().orElse(null));
    }

    // ── the years: the account's as recorded, an investor's as calculated ────────────────────────────────────────────

    private TaxYearResponse accountTaxYearResponseOf(TaxYearWithItsConfirmations taxYear) {
        List<SettlementDayResponse> settlementDays = taxYear.settlementDays().stream()
                .map(settlementDay -> settlementDayResponseOf(settlementDay.entry(), settlementDay.confirmation(),
                        settlementDay.status()))
                .toList();
        return taxYearResponseOf(taxYear.taxYear(), taxYear.recordedBalance(), settlementDays);
    }

    private InvestorTaxResponse investorTaxResponseOf(InvestorCapitalGainsTax investorTax) {
        List<TaxYearResponse> taxYears = investorTax.tax().taxYears().stream()
                .map(this::investorTaxYearResponseOf)
                .toList();
        return new InvestorTaxResponse(investorTax.investorId(), investorTax.investorName(), taxYears);
    }

    /** An investor's day has no confirmation: their tax is shown, not confirmed (decision 7). */
    private TaxYearResponse investorTaxYearResponseOf(TaxYearCalculator taxYear) {
        List<SettlementDayResponse> settlementDays = taxYear.entriesInDateOrder().stream()
                .map(entry -> settlementDayResponseOf(entry, null, entry.status()))
                .toList();
        return taxYearResponseOf(taxYear, taxYear.balance(), settlementDays);
    }

    private TaxYearResponse taxYearResponseOf(TaxYearCalculator taxYear, TaxYearBalance balance,
                                              List<SettlementDayResponse> settlementDays) {
        List<MonthlySettlementResponse> monthlySettlements = balance.monthlySettlements().stream()
                .map(this::monthlySettlementResponseOf)
                .toList();
        return new TaxYearResponse(taxYear.year(), taxYear.taxableGain(), balance.taxOfTheYearSoFar(),
                balance.shieldLeft(), settlementDays, monthlySettlements);
    }

    private MonthlySettlementResponse monthlySettlementResponseOf(MonthlySettlement monthlySettlement) {
        return new MonthlySettlementResponse(monthlySettlement.month(), monthlySettlement.entriesOfTheMonth(),
                monthlySettlement.settlementAmount(), monthlySettlement.shieldLeft(), monthlySettlement.settled());
    }

    // ── a settlement day: its sales, its expenses, its amount and its confirmation ─────────────────────────────────────

    /** {@code confirmation} is {@code null} while the day waits, and on an investor's day. */
    private SettlementDayResponse settlementDayResponseOf(SettlementDayEntry entry, TaxConfirmation confirmation,
                                                          SettlementDayStatus status) {
        List<SaleResponse> sales = entry.sales().stream().map(this::saleResponseOf).toList();
        List<TaxDeductibleExpenseResponse> expenses = entry.expenses().stream()
                .map(expense -> expenseResponseOf(entry, expense))
                .toList();
        TaxConfirmationResponse confirmationResponse =
                confirmation == null ? null : new TaxConfirmationResponse(confirmation);
        return new SettlementDayResponse(entry.settlementDay(), sales, expenses, entry.taxableGainOfTheDay(),
                entry.taxAmount(), confirmationResponse, status);
    }

    private TaxDeductibleExpenseResponse expenseResponseOf(SettlementDayEntry entry, TaxDeductibleExpense expense) {
        return new TaxDeductibleExpenseResponse(expense.ibTransactionId(), expense.currency(), expense.amount(),
                expense.description(), entry.rateOf(expense).orElse(null), entry.inShekels(expense).orElse(null));
    }

    private SaleResponse saleResponseOf(SaleTaxableGain sale) {
        TaxCalculationTrade trade = sale.sale();
        return new SaleResponse(trade.tradeId(), trade.symbol(), trade.currency(), trade.tradeDate(),
                trade.settlementDay(), trade.quantity(), trade.price(), trade.commission(),
                sale.fifoSale().quantitySoldBeyondTheBuys(), sale.notComputedReason(), sale.taxableGain(),
                sharesSoldFromEachBuyResponsesOf(sale), sale.fifoSale().fifoCostDiffersFromTheAverageCost());
    }

    /** With their taxable gain when the sale is computed; otherwise only what FIFO took, without the shekels. */
    private List<SharesSoldFromOneBuyResponse> sharesSoldFromEachBuyResponsesOf(SaleTaxableGain sale) {
        if (sale.isComputed()) {
            return sale.taxableGainsOfEachBuy().stream()
                    .map(this::sharesSoldWithTheirTaxableGainResponseOf)
                    .toList();
        }
        return sale.fifoSale().sharesSoldFromEachBuy().stream()
                .map(this::sharesSoldWithoutTheirShekelsResponseOf)
                .toList();
    }

    /** A sale not computed has no rate to take — so neither rates nor anything in shekels. */
    private SharesSoldFromOneBuyResponse sharesSoldWithoutTheirShekelsResponseOf(SharesSoldFromOneBuy sharesSold) {
        return sharesSoldResponseOf(sharesSold, null, null, null, null, null);
    }

    private SharesSoldFromOneBuyResponse sharesSoldWithTheirTaxableGainResponseOf(
            TaxableGainOfSharesFromOneBuy taxableGainOfTheShares) {
        return sharesSoldResponseOf(taxableGainOfTheShares.sharesSold(), taxableGainOfTheShares.buyRate(),
                taxableGainOfTheShares.saleRate(), taxableGainOfTheShares.gainInDollarsAtTheSaleRate(),
                taxableGainOfTheShares.gainInShekels(), taxableGainOfTheShares.taxableGain());
    }

    private SharesSoldFromOneBuyResponse sharesSoldResponseOf(SharesSoldFromOneBuy sharesSold, BigDecimal buyRate,
                                                              BigDecimal saleRate,
                                                              BigDecimal gainInDollarsAtTheSaleRate,
                                                              BigDecimal gainInShekels, BigDecimal taxableGain) {
        TaxCalculationTrade buy = sharesSold.buy();
        return new SharesSoldFromOneBuyResponse(buy.tradeId(), buy.tradeDate(), buy.settlementDay(),
                sharesSold.quantity(), sharesSold.cost(), sharesSold.proceeds(), buyRate, saleRate,
                gainInDollarsAtTheSaleRate, gainInShekels, taxableGain);
    }
}
