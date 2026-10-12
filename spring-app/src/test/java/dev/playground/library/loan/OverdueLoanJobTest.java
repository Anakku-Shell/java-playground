package dev.playground.library.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.playground.library.loan.dto.LoanResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.support.CronExpression;

/**
 * The scheduled job is a plain method: the test calls it directly instead of waiting for the cron.
 * What it does is log, so the test reads the log: {@code OutputCaptureExtension} records everything
 * written to the console during each test. Guide: §5.9 Beyond CRUD.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OverdueLoanJobTest {

    @Mock
    private LoanService loans;

    @InjectMocks
    private OverdueLoanJob job;

    @Test
    void logsEachOverdueLoan(CapturedOutput output) {
        given(loans.findOverdue())
                .willReturn(List.of(new LoanResponse(
                        5L, 1L, "Dune", 7L, Instant.parse("2026-09-10T10:00:00Z"), LocalDate.of(2026, 9, 20), null)));

        job.reportOverdueLoans();

        assertThat(output).contains("1 overdue loan(s)", "Loan 5 of 'Dune' (member 7) was due on 2026-09-20");
    }

    @Test
    void saysSoWhenNothingIsOverdue(CapturedOutput output) {
        given(loans.findOverdue()).willReturn(List.of());

        job.reportOverdueLoans();

        assertThat(output).contains("No overdue loans");
    }

    /**
     * A learning example rather than a test of our code: Spring's cron has six fields, seconds first
     * (Unix cron has five). "0 0 8 * * *" is 08:00:00 every day; "0 0 8 * * MON-FRI" would skip
     * weekends.
     */
    @Test
    void springCronHasSixFieldsSecondsFirst() {
        CronExpression cron = CronExpression.parse("0 0 8 * * *");

        assertThat(cron.next(LocalDateTime.of(2026, 10, 1, 7, 59))).isEqualTo(LocalDateTime.of(2026, 10, 1, 8, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 10, 1, 8, 0))).isEqualTo(LocalDateTime.of(2026, 10, 2, 8, 0));
    }
}
