package dev.playground.library.loan;

import dev.playground.library.loan.dto.LoanResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reports the overdue loans every morning. Here it only logs; a real library would send reminders.
 *
 * <p>{@code @Scheduled} (switched on by {@code SchedulingConfig}) runs the method on Spring's
 * scheduler thread, with no request and no logged-in user. The cron comes from
 * {@code library.jobs.overdue-cron}, default 08:00 every day: six fields, seconds first. The value
 * {@code "-"} disables the job, which the test configuration does, so no test is interrupted by it.
 * With several instances of the application, each would run it: a shared lock (ShedLock) or a
 * single scheduler instance avoids that. Guide: §5.9 Beyond CRUD.
 */
@Component
public class OverdueLoanJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueLoanJob.class);

    private final LoanService loans;

    public OverdueLoanJob(LoanService loans) {
        this.loans = loans;
    }

    @Scheduled(cron = "${library.jobs.overdue-cron:0 0 8 * * *}")
    public void reportOverdueLoans() {
        List<LoanResponse> overdue = loans.findOverdue();
        if (overdue.isEmpty()) {
            log.info("No overdue loans");
            return;
        }
        log.warn("{} overdue loan(s)", overdue.size());
        overdue.forEach(loan -> log.warn(
                "Loan {} of '{}' (member {}) was due on {}",
                loan.id(),
                loan.bookTitle(),
                loan.memberId(),
                loan.dueDate()));
    }
}
