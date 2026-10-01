package dev.playground.library.loan.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.json.JsonContent;

/**
 * The JSON contract of a loan, without a web layer: {@code @JsonTest} is the smallest slice, just
 * Boot's Jackson auto-configuration (the {@code spring.jackson.*} settings, and Jackson modules found
 * by component scanning) plus {@code JacksonTester}. The controllers' mapper is built by the same
 * auto-configuration, so a date format change shows up here first. Guide: §5.8 Testing.
 */
@JsonTest
class LoanResponseJsonTest {

    @Autowired
    private JacksonTester<LoanResponse> json;

    private final LoanResponse active = new LoanResponse(
            5L, 1L, "Dune", 7L, Instant.parse("2026-09-30T10:00:00Z"), LocalDate.of(2026, 10, 14), null);

    @Test
    void datesAreIsoStringsAndAnActiveLoanHasANullReturnedAt() throws Exception {
        JsonContent<LoanResponse> written = json.write(active);

        assertThat(written).isStrictlyEqualToJson("""
                {
                  "id": 5, "bookId": 1, "bookTitle": "Dune", "memberId": 7,
                  "loanedAt": "2026-09-30T10:00:00Z",
                  "dueDate": "2026-10-14",
                  "returnedAt": null
                }
                """);
        // JSONPath assertions, for when one field is the point.
        assertThat(written).extractingJsonPathStringValue("$.dueDate").isEqualTo("2026-10-14");
    }

    @Test
    void readsBackWhatItWrites() throws Exception {
        // A round trip: whatever a client sends back in this shape becomes the same record.
        assertThat(json.parseObject(json.write(active).getJson())).isEqualTo(active);
    }
}
