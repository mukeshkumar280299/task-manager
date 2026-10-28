package com.portal.task_manager.audit.service;

import com.portal.task_manager.audit.context.AuditContext;
import com.portal.task_manager.audit.entity.AuditLog;
import com.portal.task_manager.audit.repository.AuditRepository;
import com.portal.task_manager.entity.User;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository auditRepository;

    @Value("${audit.fallback.file.path:logs/audit_fallback.log}")
    private String fallbackFilePath;

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========== PUBLIC METHODS ==========

    /**
     * Log change for CREATE operations (where user is not yet authenticated)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logCreate(String entityName, Long entityId, String newValue, User createdUser) {
        try {
            AuditLog auditLog = buildAuditLogForCreate(entityName, entityId, newValue, createdUser);
            auditRepository.save(auditLog);
            log.debug("Audit log saved for CREATE: {} - {}", entityName, entityId);
            throw new RuntimeException("Audit recovery testing...");
        } catch (Exception e) {
            log.error("Failed to save audit log to database: {}", e.getMessage());
            writeAuditToFile(entityName, entityId, "ALL", null, newValue, "CREATE", e.getMessage(), createdUser);
        }
    }

    /**
     * Log change for UPDATE/DELETE operations (user is already authenticated)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChange(String entityName, Long entityId, String fieldName,
                          String oldValue, String newValue, String action) {
        try {
            String finalFieldName = (fieldName != null && !fieldName.isBlank()) ? fieldName : "ALL";
            AuditLog auditLog = buildAuditLogForChange(entityName, entityId, finalFieldName,
                    oldValue, newValue, action);
            auditRepository.save(auditLog);
            log.debug("Audit log saved: {} - {} - {}", entityName, action, fieldName);
        } catch (Exception e) {
            log.error("Failed to save audit log to database: {}", e.getMessage());
            writeAuditToFile(entityName, entityId, fieldName, oldValue, newValue, action, e.getMessage());
        }
    }

    // ========== PRIVATE METHODS - AUDIT LOG BUILDERS ==========

    /**
     * Build AuditLog for CREATE operation with explicit user
     */
    private AuditLog buildAuditLogForCreate(String entityName, Long entityId,
                                            String newValue, User createdUser) {
        return AuditLog.builder()
                .transactionId(generateTransactionId())
                .entityName(entityName)
                .entityId(entityId)
                .fieldName("ALL")
                .oldValue("NULL")
                .newValue(truncateValue(newValue))
                .action("CREATE")
                .modifiedBy(createdUser.getEmail())
                .modifiedById(createdUser.getId())
                .ipAddress(defaultIfNull(AuditContext.getIpAddress(), "UNKNOWN"))
                .userAgent(defaultIfNull(AuditContext.getUserAgent(), "UNKNOWN"))
                .modifiedDate(LocalDateTime.now(Clock.systemUTC()))
                .build();
    }

    /**
     * Build AuditLog for UPDATE/DELETE operation
     */
    private AuditLog buildAuditLogForChange(String entityName, Long entityId, String fieldName,
                                            String oldValue, String newValue, String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return AuditLog.builder()
                .transactionId(generateTransactionId())
                .entityName(entityName)
                .entityId(entityId)
                .fieldName(fieldName)
                .oldValue(defaultIfNull(truncateValue(oldValue), "NULL"))
                .newValue(defaultIfNull(truncateValue(newValue), "NULL"))
                .action(action)
                .modifiedBy(getCurrentUserEmail(auth))
                .modifiedById(getCurrentUserId(auth))
                .ipAddress(defaultIfNull(AuditContext.getIpAddress(), "UNKNOWN"))
                .userAgent(defaultIfNull(AuditContext.getUserAgent(), "UNKNOWN"))
                .modifiedDate(LocalDateTime.now(Clock.systemUTC()))
                .build();
    }

    // ========== PRIVATE METHODS - FILE WRITING ==========

    /**
     * Write audit to file when database save fails (without explicit user)
     */
    private void writeAuditToFile(String entityName, Long entityId, String fieldName,
                                  String oldValue, String newValue, String action,
                                  String errorReason) {
        writeAuditToFile(entityName, entityId, fieldName, oldValue, newValue, action, errorReason, null);
    }

    /**
     * Write audit to file with explicit user (for CREATE operations)
     */
    private void writeAuditToFile(String entityName, Long entityId, String fieldName,
                                  String oldValue, String newValue, String action,
                                  String errorReason, User createdUser) {
        try {
            // Build audit data
            AuditFileData auditData = buildAuditFileData(entityName, entityId, fieldName,
                    action, oldValue, newValue, createdUser);

            // Ensure directory exists
            Path filePath = Paths.get(fallbackFilePath);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // Write to file (synchronized to prevent corruption)
            synchronized (AuditService.class) {
                Files.write(
                        filePath,
                        auditData.toFileLine().getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }

            log.warn("Audit written to fallback file due to DB error: {}", errorReason);

        } catch (IOException ioException) {
            log.error("CRITICAL: Cannot write audit to file! Original error: {}, File error: {}",
                    errorReason, ioException.getMessage());
            System.err.println("AUDIT LOST - Transaction: " + generateTransactionId() +
                    ", Entity: " + entityName + ", Action: " + action);
        }
    }

    /**
     * Build audit file data from parameters
     */
    private AuditFileData buildAuditFileData(String entityName, Long entityId,
                                             String fieldName, String action,
                                             String oldValue, String newValue,
                                             User createdUser) {
        // Determine user email
        String currentUserEmail;
        if (createdUser != null) {
            currentUserEmail = createdUser.getEmail();
        } else {
            currentUserEmail = getCurrentUserEmail(SecurityContextHolder.getContext().getAuthentication());
        }

        return AuditFileData.builder()
                .timestamp(LocalDateTime.now(Clock.systemUTC()).format(FILE_DATE_FORMAT))
                .transactionId(generateTransactionId())
                .entityName(entityName)
                .entityId(String.valueOf(entityId))
                .fieldName(defaultIfNull(fieldName, "ALL"))
                .action(action)
                .oldValue(defaultIfNull(sanitizeForCsv(oldValue), "NULL"))
                .newValue(defaultIfNull(sanitizeForCsv(newValue), "NULL"))
                .modifiedBy(currentUserEmail)
                .ipAddress(defaultIfNull(AuditContext.getIpAddress(), "UNKNOWN"))
                .userAgent(defaultIfNull(AuditContext.getUserAgent(), "UNKNOWN"))
                .timestampMillis(System.currentTimeMillis())
                .build();
    }

    // ========== PRIVATE METHODS - UTILITIES ==========

    /**
     * Generate unique transaction ID
     */
    private String generateTransactionId() {
        return "TXN_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get current user email from SecurityContext
     */
    private String getCurrentUserEmail(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "SYSTEM";
        }
        return auth.getName();
    }

    /**
     * Get current user ID from SecurityContext
     */
    private Long getCurrentUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return 0L;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }

        return 0L;
    }

    /**
     * Truncate long values to prevent database issues
     */
    private String truncateValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() > 10000) {
            return value.substring(0, 10000) + "...(truncated)";
        }
        return value;
    }

    /**
     * Helper method to provide default values
     */
    private String defaultIfNull(String value, String defaultValue) {
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    /**
     * Sanitize value for CSV (remove special characters)
     */
    private String sanitizeForCsv(String value) {
        if (value == null) {
            return "NULL";
        }
        return value.replace("\n", " ").replace("\r", " ").replace("|", "\\|");
    }

    // ========== INNER CLASS ==========
    @Builder
    private static class AuditFileData {
        String timestamp;
        String transactionId;
        String entityName;
        String entityId;
        String fieldName;
        String action;
        String oldValue;
        String newValue;
        String modifiedBy;
        String ipAddress;
        String userAgent;
        long timestampMillis;

        String toFileLine() {
            return String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%s|%d\n",
                    timestamp, transactionId, entityName, entityId, fieldName,
                    action, oldValue, newValue, modifiedBy, ipAddress, userAgent, timestampMillis
            );
        }
    }
}