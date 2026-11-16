package portfolioboss.tax;

import java.util.List;

/**
 * Everything {@link CapitalGainsTaxService} works out from the stored data: the account's tax, from IB's Flex reports;
 * each other investor's, from their typed trades; and our FIFO set against IB's. The account owner's tax is what the
 * account's leaves after the other investors' (decision 6).
 *
 * @param accountTax             the account's tax — what Phoenix withholds —, year by year
 * @param otherInvestorsTax      every investor other than the account owner, in id order
 * @param ourFifoAgainstIbsFifo  the account's sales checked against IB's own FIFO
 */
record CapitalGainsTaxOfTheAccountAndInvestors(CapitalGainsTaxCalculator accountTax,
                                               List<InvestorCapitalGainsTax> otherInvestorsTax,
                                               OurFifoAgainstIbsFifo ourFifoAgainstIbsFifo) {
}
