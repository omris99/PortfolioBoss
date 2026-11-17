package portfolioboss.flex;

import java.util.Set;

/**
 * What IB's Flex Web Service answers about a request for a report when it sends no report: the request was accepted —
 * with the reference code to fetch the report by —, the report is still being prepared, or the request was refused.
 *
 * @param status        IB's {@code Success}, {@code Warn} or {@code Fail}
 * @param referenceCode the code to fetch the report by, once the request was accepted; {@code null} otherwise
 * @param errorCode     IB's error code, on {@code Warn} or {@code Fail}; {@code null} otherwise
 * @param errorMessage  IB's words for the error; {@code null} without one
 */
public record FlexReportRequestStatus(String status, String referenceCode, Integer errorCode, String errorMessage) {

    private static final String ACCEPTED_STATUS = "Success";

    /**
     * The error codes whose message ends "Please try again shortly." in IB's list: the report is still being prepared
     * (1019), part of its data isn't ready yet (1004–1008), the server is busy (1001, 1009, 1021), or too many requests
     * came from the token — one a second, ten a minute (1018). The others are permanent: a token that expired (1012) or
     * isn't valid (1015), an address IB doesn't allow (1013), a query that isn't valid (1014), …
     */
    private static final Set<Integer> TRY_AGAIN_SHORTLY_ERROR_CODES = Set.of(1001, 1004, 1005, 1006, 1007, 1008, 1009,
            1018, 1019, 1021);

    public boolean wasAccepted() {
        return ACCEPTED_STATUS.equals(status);
    }

    public boolean saysTryAgainShortly() {
        return errorCode != null && TRY_AGAIN_SHORTLY_ERROR_CODES.contains(errorCode);
    }

    /** IB's error code and words, for the console: "1012 Token has expired." */
    public String errorCodeAndMessage() {
        return errorCode + " " + errorMessage;
    }
}
