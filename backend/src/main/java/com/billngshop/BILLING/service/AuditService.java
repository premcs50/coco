package com.billngshop.BILLING.service;

import com.billngshop.BILLING.entity.AuditEvent;
import com.billngshop.BILLING.repository.AuditEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(String action, String entityType, Object entityId, String details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuditEvent event = new AuditEvent();
        event.setActor(authentication == null || !authentication.isAuthenticated()
                ? "system" : authentication.getName());
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId == null ? null : entityId.toString());
        event.setDetails(details);
        repository.save(event);
    }
}
