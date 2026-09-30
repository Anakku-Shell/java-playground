package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** ISBN checksums and the ISBN-10 → ISBN-13 conversion. Guide: §5.3 Validation & errors. */
class IsbnTest {

    @ParameterizedTest
    @ValueSource(strings = {"9780441013593", "978-0-441-01359-3", "0441013597", "0-441-01359-7", "080442957X"})
    void acceptsValidIsbn13AndIsbn10(String isbn) {
        assertThat(Isbn.isValid(isbn)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "9780441013594", // ISBN-13, last digit off by one
                "0441013598", // ISBN-10, last digit off by one
                "97804410135", // 11 digits
                "978044101359A", // a letter
                "X441013597", // X is only allowed as the ISBN-10 check digit
                "0000000000000", // checksum fine, but an ISBN-13 starts with 978 or 979
                // An Arabic-Indic 9 (U+0669) first: Character.isDigit accepts it, and with `c - '0'` as
                // its value this string even passes the checksum. ISBNs are ASCII digits only.
                "٩780441013599",
                ""
            })
    void rejectsBadChecksumsLettersAndWrongLengths(String isbn) {
        assertThat(Isbn.isValid(isbn)).isFalse();
    }

    @Test
    void convertsIsbn10ToIsbn13() {
        // Prefix 978, keep the 9 body digits, recompute the check digit with ISBN-13's 1-3 weights.
        assertThat(Isbn.toIsbn13("0441013597")).isEqualTo("9780441013593");
        assertThat(Isbn.toIsbn13("0-8044-2957-X")).isEqualTo("9780804429573");
    }

    @Test
    void stripsSeparatorsFromIsbn13() {
        assertThat(Isbn.toIsbn13("978-0-441-01359-3")).isEqualTo("9780441013593");
    }

    @Test
    void refusesToConvertAnInvalidIsbn() {
        // Validation runs first, so reaching this is a bug, not a user error.
        assertThatThrownBy(() -> Isbn.toIsbn13("123")).isInstanceOf(IllegalArgumentException.class);
    }
}
