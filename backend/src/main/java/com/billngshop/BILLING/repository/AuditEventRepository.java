package com.billngshop.BILLING.repository;

import com.billngshop.BILLING.entity.AuditEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(Instant start, Instant end);
    List<AuditEvent> findTop500ByOrderByOccurredAtDesc();
    List<AuditEvent> findAllByOrderByOccurredAtDesc();
}
