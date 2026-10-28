package com.portal.task_manager.audit.filter;

import com.portal.task_manager.audit.context.AuditContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AuditContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            AuditContext.clear();

            String ipAddress = getClientIP(request);
            String userAgent = request.getHeader("User-Agent");
            AuditContext.setIpAddress(ipAddress);
            AuditContext.setUserAgent(userAgent);

            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
        }
    }

    private String getClientIP(HttpServletRequest request) {
        String clientIPAddress = request.getHeader("X-Forwarded-For");

        if (clientIPAddress == null || clientIPAddress.isEmpty() || "unknown".equalsIgnoreCase(clientIPAddress)) {
            clientIPAddress = request.getHeader("Proxy-Client-IP");
        }

        if (clientIPAddress == null || clientIPAddress.isEmpty() || "unknown".equalsIgnoreCase(clientIPAddress)) {
            clientIPAddress = request.getHeader("WL-Proxy-Client-IP");
        }

        if (clientIPAddress == null || clientIPAddress.isEmpty() || "unknown".equalsIgnoreCase(clientIPAddress)) {
            clientIPAddress = request.getHeader("HTTP_CLIENT_IP");
        }

        if (clientIPAddress == null || clientIPAddress.isEmpty() || "unknown".equalsIgnoreCase(clientIPAddress)) {
            clientIPAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        }

        if (clientIPAddress == null || clientIPAddress.isEmpty() || "unknown".equalsIgnoreCase(clientIPAddress)) {
            clientIPAddress = request.getRemoteAddr();
        }

        // FIX: Handle localhost/IPv6 addresses
        if (clientIPAddress == null || clientIPAddress.isEmpty() || "unknown".equalsIgnoreCase(clientIPAddress)) {
            clientIPAddress = "UNKNOWN";
        }

        // Convert IPv6 localhost to readable format
        if ("0:0:0:0:0:0:0:1".equals(clientIPAddress) || "::1".equals(clientIPAddress)) {
            clientIPAddress = "127.0.0.1";
        }

        if (clientIPAddress != null && clientIPAddress.contains(",")) {
            clientIPAddress = clientIPAddress.split(",")[0].trim();
        }
        return clientIPAddress;
    }
}
