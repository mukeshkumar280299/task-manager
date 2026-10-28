package com.portal.task_manager.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "audit_log")
@ToString
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;
    @Column(name = "entity_name", nullable = false)
    private String entityName;
    @Column(name = "entity_id", nullable = false)
    private Long entityId;
    @Column(name = "field_name", nullable = false)
    private String fieldName;
    @Column(name = "old_value", nullable = false, columnDefinition = "TEXT")
    private String oldValue;
    @Column(name = "new_value", nullable = false, columnDefinition = "TEXT")
    private String newValue;
    @Column(name = "action", nullable = false)
    private String action;
    @Column(name = "modified_by", nullable = false)
    private String modifiedBy;
    @Column(name = "modified_by_id", nullable = false)
    private Long modifiedById;
    @Column(name = "ip_address", nullable = false)
    private String ipAddress;
    @Column(name = "user_agent", nullable = false)
    private String userAgent;
    @Column(name = "modified_date", nullable = false)
    private LocalDateTime modifiedDate;

}
