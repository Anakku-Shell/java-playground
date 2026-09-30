package dev.playground.library.audit;

import dev.playground.library.audit.dto.AuditEventResponse;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An audit trail of what was asked, including the requests that failed. That needs a transaction
 * of its own: in the caller's transaction, a failed request would roll its audit event back with
 * everything else.
 *
 * <p>Two choices a real system would weigh. It fails closed: if recording fails (database down,
 * pool timeout), the exception reaches the caller and its request fails too, so nothing happens
 * unaudited. And every audited request holds two connections for a moment (its own and this one):
 * fine at this scale; under load, write the event after the commit instead (§5.9) and accept that
 * a crash in between loses it. Guide: §5.6 Transactions.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * {@code REQUIRES_NEW}: the caller's transaction is suspended, this method runs in a new one on a
     * second database connection, and that one commits when the method returns. What the caller
     * does next, commit or roll back, no longer touches the event. The default ({@code REQUIRED})
     * would join the caller's transaction instead. The call goes through the Spring proxy because
     * it comes from another bean: a call from inside this class would not (self-invocation, §5.6).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String detail) {
        // Microseconds: what timestamptz stores (see LoanService.now()).
        events.save(new AuditEvent(action, detail, clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }

    /** The newest events first. */
    public List<AuditEventResponse> latest() {
        return events.findTop20ByOrderByIdDesc().stream()
                .map(AuditMapper::toResponse)
                .toList();
    }
}
