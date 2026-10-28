package com.portal.task_manager.controller.v1;

import com.portal.task_manager.audit.service.AuditService;
import com.portal.task_manager.dto.v1.LoginRequest;
import com.portal.task_manager.dto.v1.LoginResponse;
import com.portal.task_manager.dto.v1.RegisterRequest;
import com.portal.task_manager.dto.v1.UserResponse;
import com.portal.task_manager.entity.Role;
import com.portal.task_manager.entity.User;
import com.portal.task_manager.repository.UserRepository;
import com.portal.task_manager.security.JwtUtil;
import com.portal.task_manager.service.MfaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RequestMapping("/api/v1/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final MfaService mfaService;

    /**
     * Register a new user
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

        log.info("Registration request for the email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Email already registered"));
        }

        // Create new user
        User user = User.builder()
                .firstName(request.getFirstName())
                .middleName(request.getMiddleName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : Role.USER)
                .active(true)
                .createdDate(LocalDateTime.now(Clock.systemUTC()))
                .createdBy("SYSTEM")
                .createdById(0L)
                .mfaEnabled(false)
                .build();

        user = userRepository.save(user);

        // Audit the registration
        auditService.logCreate("User", user.getId(), user.getEmail(), user);

        log.info("User registered successfully: {}", user.getEmail());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponse.from(user));

    }

    /**
     * Login user
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

        log.info("Login request for email: {}", request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            Optional<User> userOptional = Optional.ofNullable(userRepository.findByEmail(request.getEmail()));
            if (userOptional.isEmpty()) {
                throw new RuntimeException("User not found");
            }
            User user = userOptional.get();

            // Check if user is active
            if (!user.isActive()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Account is disabled"));
            }

            // Check if MFA is enabled for this user
            boolean mfaRequired = mfaService.isMfaEnabled(user);
            if (mfaRequired) {
                // Generate PRE-AUTH token (limited access)
                String preAuthToken = jwtUtil.generatePreAuthToken(user.getEmail(), ((Number) user.getId()).toString(), user.getRole().name());
                auditService.logChange("User", user.getId(), "login", null, "PRE_AUTH", "LOGIN_MFA_REQUIRED");
                return ResponseEntity.ok(Map.of(
                        "mfaRequired", true,
                        "preAuthToken", preAuthToken,
                        "userId", user.getId(),
                        "email", user.getEmail()
                ));
            } else {
                // Generate FULL token (full access)
                String token = jwtUtil.generateToken(
                        user.getEmail(),
                        ((Number) user.getId()).toString(),
                        user.getRole().name()
                );

                auditService.logChange("User", user.getId(), "login",
                        null, "FULL_AUTH", "LOGIN_SUCCESS");

                return ResponseEntity.ok(LoginResponse.builder()
                        .token(token)
                        .type("Bearer")
                        .role(user.getRole().name())
                        .userId(user.getId())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .mfaEnabled(false)
                        .build());
            }

        } catch (Exception e) {
            log.warn("Login failed for email: {} - {}", request.getEmail(), e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }
    }

    /**
     * Get current logged-in user info
     * GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        String email = authentication.getName();
        Optional<User> userOptional = Optional.ofNullable(userRepository.findByEmail(email));
        if (userOptional.isEmpty()) {
            new RuntimeException("User not found");
        }

        User user = userOptional.get();

        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Check if email is available (for real-time validation)
     * GET /api/v1/auth/check-email?email=john@example.com
     */
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmailAvailability(@RequestParam String email) {

        boolean exists = userRepository.existsByEmail(email);

        return ResponseEntity.ok(Map.of(
                "email", email,
                "available", !exists,
                "message", exists ? "Email already registered" : "Email is available"
        ));
    }

    @GetMapping("/server-time")
    public ResponseEntity<?> getServerTime() {
        return ResponseEntity.ok(Map.of(
                "serverTime", LocalDateTime.now(Clock.systemUTC()),
                "serverTimeZone", Clock.systemUTC().getZone().getId(),
                "timestamp", System.currentTimeMillis()
        ));
    }
}
