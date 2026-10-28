package com.portal.task_manager.controller.v1;

import com.portal.task_manager.entity.User;
import com.portal.task_manager.repository.UserRepository;
import com.portal.task_manager.security.JwtUtil;
import com.portal.task_manager.service.MfaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequestMapping("/api/v1/mfa")
@RestController
@RequiredArgsConstructor
public class MfaController {
    private final MfaService mfaService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Step 1: Initiate MFA setup - Generate secret and QR code
     *
     */
    @PostMapping("/setup")
    public ResponseEntity<?> setupMfa(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);

        if (user.isMfaEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA is already enabled for this user"));
        }

        String secret = mfaService.generateSecret();
        String qrCode = mfaService.generateQrCode(secret,user.getEmail());
        String qrUri = mfaService.getQrUri(secret, user.getEmail());
        List<String> backupCodes = mfaService.generateBackupCodes();

        storeTempSecret(user.getId(), secret);

        Map<String, Object> response = new HashMap<>();
        response.put("secret", secret);
        response.put("qrCode", qrCode);
        response.put("qrUri", qrUri);
        response.put("backupCodes", backupCodes);

        return ResponseEntity.ok(response);
    }
    /**
     * Step 2: Verify code and enable MFA
     *
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyAndEnable(@RequestParam String secret, @RequestParam String code, @RequestParam(required = false) List<String> backupCodes, Authentication authentication) {
        User user = getUserFromAuthentication(authentication);

        boolean isValid = mfaService.verifyCode(secret, code);

        if (!isValid) {
            log.warn("Invalid MFA verification code for user: {}", user.getEmail());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid verification code"));
        }

        mfaService.enableMfa(user, secret, backupCodes);

        log.info("MFA enabled successfully for user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("message", "MFA enabled successfully", "mfaEnabled", true));
    }
    /**
     * Disable MFA for current user
     *
     */
    @PostMapping("/disable")
    public ResponseEntity<?> disableMfa(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);

        if (!user.isMfaEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "MFA is not enabled for this user"));
        }

        mfaService.disableMfa(user);
        log.info("MFA disabled for the user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("message", "MFA disabled successfully", "mfaEnabled", false));
    }
    /**
     * Get MFA status for current user
     *
     */
    public ResponseEntity<?> getMfaStatus(Authentication authentication) {
        User user = getUserFromAuthentication(authentication);
        return ResponseEntity.ok(Map.of("mfaEnabled", user.isMfaEnabled(), "email", user.getEmail(), "enabledAt", user.getMfaEnabledAt(), "hasBackupCodes", user.getBackupCodesHash() != null));
    }
    /**
     * Verify MFA code during login (using pre-auth token)
     * POST /api/v1/mfa/verify-login
     */
    @PostMapping("/verify-login")
    public ResponseEntity<?> verifyLoginMfa(@RequestParam String code, @RequestParam String preAuthToken) {
        // Validate pre-auth token
        if (!jwtUtil.validateToken(preAuthToken)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired token"));
        }
        // Check if it's a pre-auth token
        if (!jwtUtil.isPreAuthToken(preAuthToken)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid token type"));
        }

        // Verify the TOTP code
        String email = jwtUtil.getEmailFromToken(preAuthToken);
        Optional<User> userOptional = Optional.ofNullable(userRepository.findByEmail(email));
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found.");
        }
        User user = userOptional.get();

        // Verify the TOTP code
        boolean isValid = mfaService.verifyCode(user.getMfaSecret(), code);

        if (!isValid) {
            log.warn("Invalid MFA login code for user: {}", email);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid verification code"));
        }

        // Update last used timestamp
        user.setMfaLastUsedAt(LocalDateTime.now(Clock.systemUTC()));
        userRepository.save(user);

        // Generate FULL token
        String fullToken = jwtUtil.generateToken(user.getEmail(), ((Number) user.getId()).toString(), user.getRole().name());
        log.info("MFA verification successful for user: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("token", fullToken, "type", "Bearer", "role", user.getRole().name(), "userId", user.getId(), "mfaVerified", true));
    }
    /**
     * Verify using backup code (when phone is lost)
     *
     */
    @PostMapping("/verify-backup")
    public ResponseEntity<?> verifyBakcupCode(@RequestParam String backupCode, @RequestParam String preAuthToken) {
        if (!jwtUtil.validateToken(preAuthToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or expired token"));
        }
        String email = jwtUtil.getEmailFromToken(preAuthToken);
        Optional<User> userOptional = Optional.ofNullable(userRepository.findByEmail(email));
        if (userOptional.isEmpty()) {
            log.warn("Invalid MFA login code for user: {}", email);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid verification code"));
        }
        User user = userOptional.get();

        boolean isValid = mfaService.verifyBackupCode(user, backupCode);

        if (!isValid) {
            log.warn("Invalid backup code for user: {}", email);
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid backup code"));
        }

        // Generate FULL token
        String fullToken = jwtUtil.generateToken(
                user.getEmail(),
                ((Number)user.getId()).toString(),
                user.getRole().name()
        );

        log.info("Backup code used successfully for user: {}", email);

        return ResponseEntity.ok(Map.of(
                "token", fullToken,
                "type", "Bearer",
                "role", user.getRole().name(),
                "userId", user.getId(),
                "backupCodeUsed", true,
                "remainingBackupCodes", "Check your profile"
        ));
    }

    private User getUserFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }
        String email = authentication.getName();
        Optional<User> userOptional = Optional.ofNullable(userRepository.findByEmail(email));
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found");
        }
        User user = userOptional.get();

        return user;

    }

    /**
     * Store temporary secret for verification
     *
     */
    private void storeTempSecret(Long userId, String secret) {
        // For now, just log. In production, will implement cache.
        log.debug("Temporary secret stored for user: {}", userId);
    }
}
