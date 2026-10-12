package dev.playground.library.audit;

import dev.playground.library.audit.dto.AuditEventResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The latest 20 audit events, newest first, to watch {@code REQUIRES_NEW} at work: a rejected
 * borrow leaves no loan, but it leaves an event. Guide: §5.6 Transactions.
 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuditEventResponse> latest() {
        return service.latest();
    }
}
