package portfolioboss.calculation.tax;

import portfolioboss.calculation.splits.StockSplit;
import portfolioboss.calculation.splits.StoredStockSplits;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.flex.FlexReportTrade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The splits in the Corporate Actions section of IB's Flex reports (STOCK_SPLITS_TODO.md, decision 3): an action is
 * understood as a split only when IB's words and the shares agree, so that what is stored is never a guess. Two kinds
 * are understood:
 * <ul>
 *   <li>a split in the same contract — one row, the shares it adds: "SPLIT 4 FOR 1" of 10 shares held adds 30;</li>
 *   <li>a split into a new contract — two rows at the same time, the old contract's shares taken away and the new
 *       contract's added: "SPLIT 1 FOR 10" of 1,000 shares is −1,000 and +100.</li>
 * </ul>
 * The shares held just before an action are what the account's Flex trades add up to, after the splits understood before
 * it ({@link FlexTradesAfterTheSplits#sharesHeldJustBefore}). Anything else is a {@link SplitNotUnderstood}, with its
 * reason. Only actions of a split's type are looked at: a spin-off or a merger is left to the tax, which computes none of
 * its sales.
 *
 * @param corporateActions every stored row of a corporate action
 * @param flexTrades       every stored Flex trade: the shares held just before each action
 */
public record SplitsInFlexCorporateActions(List<FlexReportCorporateAction> corporateActions,
                                           List<FlexReportTrade> flexTrades) {

    /** IB's types of a split ({@code FS}) and of a reverse split ({@code RS}). */
    private static final Set<String> SPLIT_TYPES = Set.of("FS", "RS");

    private static final String STOCK = "STK";

    /** IB's words, "ABCD(US0000000000) SPLIT 4 FOR 1 (ABCD, …)": the shares after the split, then the shares before. */
    private static final Pattern SPLIT_IN_IBS_WORDS = Pattern.compile("\\bSPLIT (\\d{1,6}) FOR (\\d{1,6})\\b");

    /** Below this two quantities count as the same: a split of 1 for 3 leaves a fraction 16 digits can't hold exactly. */
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.000001");

    /** The splits understood, the earliest first. */
    public List<StockSplit> splitsUnderstood() {
        List<StockSplit> splitsUnderstood = new ArrayList<>();
        for (CorporateAction action : splitActionsInTimeOrder()) {
            if (whyNotUnderstood(action, splitsUnderstood).isEmpty()) {
                splitsUnderstood.add(action.splitInIbsWords().orElseThrow());
            }
        }
        return splitsUnderstood;
    }

    /** The actions of a split's type not understood, the earliest first, each with its reason. */
    public List<SplitNotUnderstood> splitsNotUnderstood() {
        List<StockSplit> splitsUnderstood = splitsUnderstood();
        List<SplitNotUnderstood> splitsNotUnderstood = new ArrayList<>();
        for (CorporateAction action : splitActionsInTimeOrder()) {
            List<StockSplit> splitsUnderstoodBeforeIt = splitsUnderstood.stream()
                    .filter(split -> split.splitTime().isBefore(action.actionTime()))
                    .toList();
            whyNotUnderstood(action, splitsUnderstoodBeforeIt).ifPresent(reason -> splitsNotUnderstood.add(
                    new SplitNotUnderstood(action.ibActionId(), action.symbol(), action.actionTime(), reason)));
        }
        return splitsNotUnderstood;
    }

    /** Empty when the action is understood as a split. The checks in this order, the first that fails its reason. */
    private Optional<WhySplitNotUnderstood> whyNotUnderstood(CorporateAction action,
                                                             List<StockSplit> splitsUnderstoodBeforeIt) {
        if (!action.isOfASplitsKind()) {
            return Optional.of(WhySplitNotUnderstood.ROWS_OF_ANOTHER_KIND);
        }
        if (action.bringsMoney()) {
            return Optional.of(WhySplitNotUnderstood.IT_BRINGS_MONEY);
        }
        Optional<StockSplit> splitInIbsWords = action.splitInIbsWords();
        if (splitInIbsWords.isEmpty()) {
            return Optional.of(WhySplitNotUnderstood.NO_SPLIT_IN_IBS_WORDS);
        }
        BigDecimal sharesHeldJustBefore = new FlexTradesAfterTheSplits(flexTrades,
                new StoredStockSplits(splitsUnderstoodBeforeIt), List.of())
                .sharesHeldJustBefore(splitInIbsWords.get().conIdBeforeTheSplit(), action.actionTime());
        if (!action.movesTheSharesOf(splitInIbsWords.get(), sharesHeldJustBefore)) {
            return Optional.of(WhySplitNotUnderstood.SHARES_DONT_MATCH);
        }
        return Optional.empty();
    }

    /** The rows grouped into actions by IB's {@code actionID}, only those of a split's type, the earliest first. */
    private List<CorporateAction> splitActionsInTimeOrder() {
        Map<String, List<FlexReportCorporateAction>> rowsByActionId = new LinkedHashMap<>();
        for (FlexReportCorporateAction row : corporateActions) {
            rowsByActionId.computeIfAbsent(row.ibActionId(), actionId -> new ArrayList<>()).add(row);
        }
        return rowsByActionId.values().stream()
                .map(CorporateAction::new)
                .filter(CorporateAction::isOfASplitsType)
                .sorted(Comparator.comparing(CorporateAction::actionTime))
                .toList();
    }

    /**
     * The rows of one corporate action, as IB grouped them by {@code actionID}.
     *
     * @param rows the action's rows, in IB's order
     */
    private record CorporateAction(List<FlexReportCorporateAction> rows) {

        private String ibActionId() {
            return rows.getFirst().ibActionId();
        }

        private LocalDateTime actionTime() {
            return rows.getFirst().actionTime();
        }

        private String symbol() {
            return rows.getFirst().symbol();
        }

        private boolean isOfASplitsType() {
            return rows.stream().allMatch(row -> SPLIT_TYPES.contains(row.actionType()));
        }

        /**
         * Rows of a stock, at one time: one row that adds or takes away shares (a split in the same contract), or two —
         * one taking shares away and one adding them (a split into a new contract).
         */
        private boolean isOfASplitsKind() {
            boolean rowsOfAStockAtOneTime = rows.stream().allMatch(row ->
                    STOCK.equals(row.assetCategory()) && row.actionTime().equals(actionTime()));
            if (!rowsOfAStockAtOneTime) {
                return false;
            }
            if (isInTheSameContract()) {
                return rows.getFirst().quantity().signum() != 0;
            }
            return rows.size() == 2 && rows.get(0).quantity().signum() * rows.get(1).quantity().signum() < 0;
        }

        private boolean isInTheSameContract() {
            return rows.size() == 1;
        }

        private boolean bringsMoney() {
            return rows.stream().anyMatch(row -> row.proceeds().signum() != 0 || row.amount().signum() != 0);
        }

        /**
         * The split IB's words state — the same numbers in every row —, with the contracts of before and after it. Empty
         * when a row doesn't say "SPLIT n FOR m", or the rows say different ones.
         */
        private Optional<StockSplit> splitInIbsWords() {
            String sharesAfterTheSplit = null;
            String sharesBeforeTheSplit = null;
            for (FlexReportCorporateAction row : rows) {
                Matcher wordsOfTheRow = SPLIT_IN_IBS_WORDS.matcher(row.description() == null ? "" : row.description());
                if (!wordsOfTheRow.find()) {
                    return Optional.empty();
                }
                if (sharesAfterTheSplit == null) {
                    sharesAfterTheSplit = wordsOfTheRow.group(1);
                    sharesBeforeTheSplit = wordsOfTheRow.group(2);
                } else if (!wordsOfTheRow.group(1).equals(sharesAfterTheSplit)
                        || !wordsOfTheRow.group(2).equals(sharesBeforeTheSplit)) {
                    return Optional.empty();
                }
            }
            int sharesBefore = Integer.parseInt(sharesBeforeTheSplit);
            int sharesAfter = Integer.parseInt(sharesAfterTheSplit);
            if (sharesBefore == 0 || sharesAfter == 0) {
                return Optional.empty();
            }
            return Optional.of(new StockSplit(ibActionId(), rowOfTheSharesAfter().symbol(),
                    rowOfTheSharesBefore().conId(), rowOfTheSharesAfter().conId(), actionTime(), sharesBefore,
                    sharesAfter));
        }

        /**
         * Whether the shares the rows move are the split's of the shares held just before it. In the same contract: the
         * shares added (or taken away) are the shares after it less those before. Into a new contract: the old
         * contract's shares taken away are those held, and the new contract's added are the shares after it. Nothing
         * held just before is a trade missing: IB books no split of a stock not held.
         */
        private boolean movesTheSharesOf(StockSplit split, BigDecimal sharesHeldJustBefore) {
            if (sharesHeldJustBefore.signum() == 0) {
                return false;
            }
            BigDecimal sharesAfterTheSplit = split.quantityAfterTheSplit(sharesHeldJustBefore);
            if (isInTheSameContract()) {
                return areTheSame(sharesAfterTheSplit.subtract(sharesHeldJustBefore), rows.getFirst().quantity());
            }
            return areTheSame(sharesHeldJustBefore, rowOfTheSharesBefore().quantity().negate())
                    && areTheSame(sharesAfterTheSplit, rowOfTheSharesAfter().quantity());
        }

        /** The row taking the old contract's shares away; the one row of a split in the same contract. */
        private FlexReportCorporateAction rowOfTheSharesBefore() {
            if (isInTheSameContract()) {
                return rows.getFirst();
            }
            return rows.get(0).quantity().signum() < 0 ? rows.get(0) : rows.get(1);
        }

        /** The row adding the new contract's shares; the one row of a split in the same contract. */
        private FlexReportCorporateAction rowOfTheSharesAfter() {
            if (isInTheSameContract()) {
                return rows.getFirst();
            }
            return rows.get(0).quantity().signum() > 0 ? rows.get(0) : rows.get(1);
        }

        private boolean areTheSame(BigDecimal quantity, BigDecimal otherQuantity) {
            return quantity.subtract(otherQuantity).abs().compareTo(QUANTITY_TOLERANCE) <= 0;
        }
    }
}
