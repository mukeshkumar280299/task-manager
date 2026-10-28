package com.portal.task_manager.service;

import com.portal.task_manager.entity.User;
import com.portal.task_manager.repository.UserRepository;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {
    private final UserRepository userRepository;

    @Value("${mfa.totp.issuer:TaskManager}")
    private String issuer;

    @Value("${mfa.totp.digits:6}")
    private int digits;

    @Value("${mfa.totp.period:30}")
    private int period;

    @Value("${mfa.backup-codes.count:8}")
    private int backupCodesCount;

    /**
     * Generate a new secret key for MFA
     */
    public String generateSecret() {
        SecretGenerator secretGenerator = new DefaultSecretGenerator();
        return secretGenerator.generate();
    }

    /**
     * Generate QR code as Base64 encoded image
     */
    public String generateQrCode(String secret, String email) {
        try {
            QrData qrData = new QrData.Builder()
                    .label(email)
                    .issuer(issuer)
                    .digits(digits)
                    .period(period)
                    .algorithm(HashingAlgorithm.SHA1)
                    .build();

            QrGenerator qrGenerator = new ZxingPngQrGenerator();
            byte[] qrCode = qrGenerator.generate(qrData);
            return Base64.getEncoder().encodeToString(qrCode);
        } catch (QrGenerationException e) {
            log.error("Failed to generate QR code: {}", e.getMessage());
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
    /**
     * Get QR code URI (for manual entry)
     */
    public String getQrUri(String secret, String email) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                issuer, email, secret, issuer, digits, period);
    }
    /**
     * Verify TOTP code against secret
     */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) {
            return false;
        }

        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();

        // Get current time bucket
        long currentTimeBucket = timeProvider.getTime() / 30L;

        // Check 9 buckets in each direction (total 8 minutes window)
        // This covers your 4-minute difference
        try {
            for (int i = -9; i <= 9; i++) {
                long timeBucket = currentTimeBucket + i;
                String expectedCode = codeGenerator.generate(secret, timeBucket);

                if (expectedCode.equals(code)) {
                    log.info("Code matched at offset: {}", i);
                    return true;
                }
            }
        } catch (CodeGenerationException ex) {
            log.error("Exception Raised - {}", ex.getMessage());
        }

        return false;

    }
    /**
     * Generate backup codes for user
     */
    public List<String> generateBackupCodes() {
        List<String> backupCodes = new ArrayList<>();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < backupCodesCount; i++) {
            String code = String.format("%04d-%04d", random.nextInt(10000),random.nextInt(10000));
            backupCodes.add(code);
        }
        return backupCodes;
    }
    /**
     * Enable MFA for user after verification
     */
    public void enableMfa(User user,String secret, List<String> backupCodes) {
        user.setMfaEnabled(true);
        user.setMfaSecret(secret);
        user.setBackupCodesHash(hashBackupCodes(backupCodes));
        user.setMfaEnabledAt(LocalDateTime.now(Clock.systemUTC()));
        userRepository.save(user);

        log.info("MFA enabled for user: {}", user.getEmail());
    }

    /**
     * Disable MFA for user
     */
    public void disableMfa(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setBackupCodesHash(null);
        user.setMfaEnabledAt(null);
        userRepository.save(user);

        log.info("MFA disabled for user: {}", user.getEmail());
    }
    /**
     * Verify backup code (simplified - in production, store hashed)
     */
    public boolean verifyBackupCode(User user, String code) {
        String storedCodes = user.getBackupCodesHash();
        if (storedCodes == null) {
            return false;
        }

        if (storedCodes.contains(code)) {
            String updatedCodes = storedCodes.replace(code,"").replace(",,",",");
            user.setBackupCodesHash(updatedCodes);
            userRepository.save(user);
            return true;
        }

        return false;
    }
    /**
     * Hash backup codes (simplified - use BCrypt in production)
     */
    private String hashBackupCodes(List<String> codes) {
        return String.join(",", codes);
    }

    /**
     * Check if user has MFA enabled
     */
    public boolean isMfaEnabled(User user) {
        return user != null && user.isMfaEnabled();
    }

}
