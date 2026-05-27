package com.eduplatform.user.util;

public class UserAgentParser {

    public static String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Device";
        }

        String browser = detectBrowser(userAgent);
        String os = detectOS(userAgent);

        if ("Unknown".equals(browser) && "Unknown".equals(os)) {
            return "Unknown Device";
        }

        return browser + " on " + os;
    }

    private static String detectBrowser(String ua) {
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) return "Safari";
        if (ua.contains("Firefox/")) return "Firefox";
        return "Unknown";
    }

    private static String detectOS(String ua) {
        if (ua.contains("Windows NT")) return "Windows";
        if (ua.contains("Mac OS X") || ua.contains("macOS")) return "macOS";
        if (ua.contains("Linux") && !ua.contains("Android")) return "Linux";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) return "iOS";
        return "Unknown";
    }
}