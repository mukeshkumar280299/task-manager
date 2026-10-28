package com.portal.task_manager.audit.service;

import com.portal.task_manager.audit.entity.AuditLog;
import com.portal.task_manager.audit.repository.AuditRepository;
import com.portal.task_manager.entity.User;
import com.portal.task_manager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditRecoveryService {
    private final AuditRepository auditRepository;

    private final UserRepository userRepository;
    private static final String FALLBACK_FILE = "logs/audit_fallback.log";
    private static final String PROCESSED_FILE = "logs/adudit_fallback_processed.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * Run every hour to recover failed audits
     * Cron: second minute hour day month weekday
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    public void recoverFailedAudits() {
        log.info("Starting audit recovery job...");

        Path filePath = Paths.get(FALLBACK_FILE);
        if (!Files.exists(filePath)) {
            log.debug("No fallback audit file found");
            return;
        }

        try {
            //Read all lines from file
            List<String> lines = Files.readAllLines(filePath);

            if (lines.isEmpty()) {
                log.debug("Fallback audit file is empty");
                return;
            }

            //Process each line
            List<String> failedLines = new ArrayList<>();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (String line: lines) {
                if (processAuditLine(line)) {
                    successCount.incrementAndGet();
                } else {
                    failedLines.add(line);
                    failCount.incrementAndGet();
                }
            }
            // Write back only failed lines to original file
            if (failedLines.isEmpty()) {
                //All successful - delete the file
                Files.deleteIfExists(filePath);
                log.info("All {} audit record recovered successfully. File deleted.", successCount.get());
            } else {
                //Write failed lines back to file
                Files.write(filePath, failedLines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.warn("Recovered: {}, Failed: {} (will retry later)", successCount.get(), failCount.get());
            }

            // Archive processed records
            archiveProcessedRecords(lines, failedLines);

        } catch (IOException e) {
            log.error("Failed to recover audits from file: {}", e.getMessage(), e);
        }

    }
    /**
     * Process a single audit line and save to database
     * @return true if successful, false otherwise
     */
    private boolean processAuditLine(String line) {
        try {
            // Parse CSV line (format: timestamp|transactionId|entityName|entityId|...)
            String[] parts = line.split("\\|");
            if (parts.length < 12) {
                log.warn("Invalid audit line format: {}", line);
                return false;
            }
            String userAgent = parts.length > 11 ? parts[11] : "UNKNOWN";
            // Build AuditLog entity from parsed data
            AuditLog auditLog = AuditLog.builder()
                    .modifiedDate(LocalDateTime.parse(parts[0], FORMATTER))
                    .transactionId(parts[1])
                    .entityName(parts[2])
                    .entityId(Long.parseLong(parts[3]))
                    .fieldName(parts[4])
                    .action(parts[5])
                    .oldValue("NULL".equals(parts[6]) ? "NULL" : parts[6])
                    .newValue("NULL".equals(parts[7]) ? "NULL" : parts[7])
                    .modifiedBy(parts[8])
                    .modifiedById(getUserIdFromEmail(parts[8]))
                    .ipAddress(parts[9])
                    .userAgent(userAgent)
                    .build();
            auditRepository.save(auditLog);
            log.debug("Recovered audit record: {}", auditLog.getTransactionId());
            return true;
        } catch (Exception e) {
            log.error("Failed to process audit line: {} - Error: {}", line, e.getMessage());
            return false;
        }
    }
    /**
     * Get user ID from email address
     */
    private Long getUserIdFromEmail(String email) {
        if (email == null || "SYSTEM".equals(email) || "UNKNOWN".equals(email) || "anonymousUser".equals(email)) {
            return 0L;
        }

        try {
            Optional<User> userOpt = Optional.ofNullable(userRepository.findByEmail(email));
            if (userOpt.isPresent()) {
                return userOpt.get().getId();
            } else {
                log.warn("User not found for email: {}", email);
                return 0L;
            }
        } catch (Exception e) {
            log.warn("Could not find user ID for email: {} - {}", email, e.getMessage());
            return 0L;
        }
    }
    /**
     * Archive processed records for audit trail (optional)
     */
    private void archiveProcessedRecords(List<String> allLines, List<String> failedLines) {
        try {
            List<String> processedLines = new ArrayList<>(allLines);
            processedLines.removeAll(failedLines);

            if (!processedLines.isEmpty()) {
                // Append to archive file
                Files.write(Paths.get(PROCESSED_FILE), processedLines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                // Add timestamp to archived records
                String timestamp = "# Processed on: " + LocalDateTime.now() + "\n";
                Files.write(Paths.get(PROCESSED_FILE), timestamp.getBytes(),
                        StandardOpenOption.APPEND);
            }

        } catch (Exception e) {
            log.warn("Failed to archive processed records: {}", e.getMessage());
        }
    }
    /**
     * Manual trigger for recovery (can be called via API)
     */
    public void triggerManualRecovery() {
        log.info("Manual audit recovery triggered");
        recoverFailedAudits();
    }
}
