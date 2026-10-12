package dev.playground.library.messaging;

import dev.playground.library.messaging.dto.BookLoanCount;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the Kafka consumer has counted so far (LIBRARIAN, {@code SecurityConfig}). Without Kafka the
 * controller does not exist and the URL is a 404. Guide: §5.10 Kafka.
 */
@RestController
@RequestMapping("/api/stats")
@ConditionalOnBooleanProperty("library.kafka.enabled")
public class LoanStatsController {

    private final LoanStatsConsumer stats;

    public LoanStatsController(LoanStatsConsumer stats) {
        this.stats = stats;
    }

    @GetMapping("/loans")
    public List<BookLoanCount> loans() {
        return stats.ranking();
    }
}
