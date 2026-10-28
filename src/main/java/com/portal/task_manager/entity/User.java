package com.portal.task_manager.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Clock;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @Column(name = "first_name", nullable = false)
    private String firstName;
    @Column(name = "middle_name", nullable = true)
    private String middleName;
    @Column(name = "last_name", nullable = true)
    private String lastName;
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    @Column(name = "password", nullable = false)
    private String password;
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;
    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;
    @Column(name = "mfa_secret")
    private String mfaSecret;
    @Column(name = "backup_codes_hash")
    private String backupCodesHash;
    @Column(name = "mfa_enabled_at")
    private LocalDateTime mfaEnabledAt;
    @Column(name = "mfa_last_used_at")
    private LocalDateTime mfaLastUsedAt;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
    @Column(name = "created_by_id", nullable = false, updatable = false)
    private Long createdById;
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;
    public String getDisplayName() {
        StringBuilder name = new StringBuilder();
        name.append(firstName);
        if (middleName != null && !middleName.isBlank()) {
            name.append(" ").append(middleName);
        }
        if (lastName != null && !lastName.isBlank()) {
            name.append(" ").append(lastName);
        }
        return name.toString();
    }
    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now(Clock.systemUTC());

        if (createdBy == null) {
            createdBy = "SYSTEM";
        }
        if (createdById == null) {
            createdById = 0L;
        }
    }
}
