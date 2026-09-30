package dev.playground.library.book;

import java.util.Locale;

/**
 * ISBN rules in one place: the {@code @ValidIsbn} constraint checks with {@link #isValid}, and the
 * mapper stores every ISBN as 13 digits with {@link #toIsbn13}, so "0441013597" and
 * "978-0-441-01359-3" are the same book. Guide: §5.3 Validation & errors.
 */
public final class Isbn {

    private Isbn() {}

    /**
     * An ISBN-13 (prefix 978 or 979), or an ISBN-10 (check digit 0–9 or X). Hyphens and spaces are
     * ignored wherever they are: the check is on the digits, not on the formatting.
     */
    public static boolean isValid(String isbn) {
        String s = strip(isbn);
        return (s.length() == 13
                        && s.chars().allMatch(Isbn::isAsciiDigit)
                        && (s.startsWith("978") || s.startsWith("979"))
                        && isbn13CheckDigit(s) == s.charAt(12))
                || (s.length() == 10 && isValidIsbn10(s));
    }

    /** The ISBN as 13 bare digits. Throws for an invalid ISBN: validation must have run first. */
    public static String toIsbn13(String isbn) {
        if (!isValid(isbn)) {
            throw new IllegalArgumentException("Not a valid ISBN: " + isbn);
        }
        String s = strip(isbn);
        if (s.length() == 13) {
            return s;
        }
        String body = "978" + s.substring(0, 9);
        return body + isbn13CheckDigit(body);
    }

    private static String strip(String isbn) {
        // Locale.ROOT: toUpperCase follows the machine's locale otherwise (§4.1).
        return isbn.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    // Character.isDigit would also accept Arabic-Indic, Devanagari... digits, and `c - '0'` would then
    // give nonsense values.
    private static boolean isAsciiDigit(int c) {
        return c >= '0' && c <= '9';
    }

    // Weights 1, 3, 1, 3... over the first 12 digits; the check digit brings the sum to a multiple of 10.
    private static char isbn13CheckDigit(String digits) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (digits.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return (char) ('0' + (10 - sum % 10) % 10);
    }

    // Weights 10 down to 1; the weighted sum must be a multiple of 11. 'X' stands for 10.
    private static boolean isValidIsbn10(String s) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = s.charAt(i);
            int value;
            if (isAsciiDigit(c)) {
                value = c - '0';
            } else if (c == 'X' && i == 9) {
                value = 10;
            } else {
                return false;
            }
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }
}
