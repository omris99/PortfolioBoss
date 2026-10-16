package portfolioboss.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The helpers that hold a rule of their own. Pure computation — no Spring, no database. */
class UtilsTest {

    @Test
    void blankTextIsNothingEnteredAndOtherTextLosesItsSurroundingSpaces() {
        assertThat(Utils.trimmedOrNull(null)).isNull();
        assertThat(Utils.trimmedOrNull("   ")).isNull();
        assertThat(Utils.trimmedOrNull("  Technology ")).isEqualTo("Technology");
    }
}
