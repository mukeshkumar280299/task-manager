package com.portal.task_manager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.PropagationContextElement;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private Long expiration;
    @Value("${jwt.pre-auth-expiration}")
    private Long preAuthExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate FULL JWT token (after password + MFA verification)
     */
    public String generateToken(String email, String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("type", "Full");

        return buildToken(claims, email, expiration);
    }
    /**
     * Generate PRE-AUTH JWT token (after password only, MFA pending)
     */
    public String generatePreAuthToken(String email, String userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("type", "pre-auth");
        claims.put("mfaPending", true);

        return buildToken(claims, email, preAuthExpiration);

    }

    private String buildToken(Map<String, Object> claims, String subject, Long validity) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + validity);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);

            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }
    /**
     * Get email (subject) from token
     */
    public String getEmailFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.getSubject() : null;
    }
    /**
     * Get user ID from token
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? ((Number) claims.get("userId")).longValue() : null;
    }
    /**
     * Get role from token
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.get("role", String.class) : null;
    }
    /**
     * Check if token is PRE-AUTH type (MFA pending)
     */
    public boolean isPreAuthToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null && "pre-auth".equals(claims.get("type"));
    }
    /**
     * Check if token is FULL type
     */
    public boolean isFullAuthToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null && "full".equals(claims.get("type"));
    }
    /**
     * Get token expiration time
     */
    public Date getExpirationDate(String token) {
        Claims claims = parseClaims(token);
        return claims != null ? claims.getExpiration() : null;
    }
    /**
     * Get remaining validity in milliseconds
     */
    public long getRemainingExpiry(String token) {
        Date expiry = getExpirationDate(token);
        if (expiry == null) return 0;
        return Math.max(0, expiry.getTime() - System.currentTimeMillis());
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
        } catch (JwtException e) {
            log.warn("Failed to parse JWT claims: {}", e.getMessage());
            return null;
        }
    }
}
