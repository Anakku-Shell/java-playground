package dev.playground.library.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** The audit trail. Guide: §5.6 Transactions. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    // "Top20" limits the result: select ... order by id desc fetch first 20 rows only.
    List<AuditEvent> findTop20ByOrderByIdDesc();
}
