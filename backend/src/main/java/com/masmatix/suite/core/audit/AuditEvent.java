package com.masmatix.suite.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Append-only record of who changed what and when. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "occurred_at", nullable = false)
    private Instant at;
    @Column(nullable = false, length = 80)
    private String username;
    @Column(nullable = false, length = 20)
    private String action;
    @Column(nullable = false, length = 80)
    private String entity;
    private Long entityId;
    @Column(length = 500)
    private String detail;

    protected AuditEvent() {}

    public AuditEvent(String username, String action, String entity, Long entityId, String detail) {
        this.at = Instant.now();
        this.username = username;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getEntity() { return entity; }
    public Long getEntityId() { return entityId; }
    public String getDetail() { return detail; }
}
