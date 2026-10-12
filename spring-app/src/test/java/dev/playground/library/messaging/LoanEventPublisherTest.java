package dev.playground.library.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import dev.playground.library.loan.LoanCreatedEvent;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * What the publisher hands to Kafka, with {@code KafkaTemplate} mocked. Guide: §5.10 Kafka.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LoanEventPublisherTest {

    private static final LoanCreatedEvent DUNE_FOR_ADA =
            new LoanCreatedEvent(7L, 3L, "Dune", 2L, LocalDate.of(2026, 10, 14));

    @Mock
    private KafkaTemplate<String, LoanMessage> kafka;

    @Test
    void sendsTheLoanKeyedByItsBook() {
        given(kafka.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());

        new LoanEventPublisher(kafka).onLoanCreated(DUNE_FOR_ADA);

        // The key picks the partition: every loan of book 3 lands in the same one, in order.
        then(kafka).should().send("library.loans", "3", new LoanMessage(7L, 3L, 2L, LocalDate.of(2026, 10, 14)));
    }

    @Test
    void aFailedSendIsLoggedNotThrown(CapturedOutput output) {
        CompletableFuture<SendResult<String, LoanMessage>> failed =
                CompletableFuture.failedFuture(new TimeoutException("no broker"));
        given(kafka.send(anyString(), anyString(), any())).willReturn(failed);

        new LoanEventPublisher(kafka).onLoanCreated(DUNE_FOR_ADA);

        assertThat(output).contains("Loan 7 was not published to library.loans");
    }
}
