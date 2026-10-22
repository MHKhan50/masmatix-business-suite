package com.masmatix.suite.core.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String entity, Long entityId, String detail) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = auth == null ? "system" : auth.getName();
        recordAs(user, action, entity, entityId, detail);
    }

    public void recordAs(String username, String action, String entity, Long entityId, String detail) {
        repository.save(new AuditEvent(username, action, entity, entityId, detail));
    }
}
