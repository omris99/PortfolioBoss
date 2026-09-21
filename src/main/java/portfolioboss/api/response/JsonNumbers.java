package portfolioboss.api.response;

/**
 * JSON has no {@code NaN} or infinity: depending on the library a writer either fails or emits the
 * <em>string</em> {@code "NaN"}, which breaks the UI's {@code number | null} types. A figure IB did
 * not report must reach the UI as {@code null}.
 */
final class JsonNumbers {

    static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private JsonNumbers() {
    }
}
