package com.portal.task_manager.audit.context;

public class AuditContext {
    private static final ThreadLocal<String> currentIpAddress = new ThreadLocal<>();
    private static final ThreadLocal<String> currentUserAgent = new ThreadLocal<>();

    public static void setIpAddress(String ipAddress) {
        currentIpAddress.set(ipAddress);
    }

    public static String getIpAddress() {
        return currentIpAddress.get();
    }

    public static void setUserAgent(String userAgent) {
        currentUserAgent.set(userAgent);
    }

    public static String getUserAgent() {
        return currentUserAgent.get();
    }

    public static void clear()  {
        currentIpAddress.remove();
        currentUserAgent.remove();
    }
}
