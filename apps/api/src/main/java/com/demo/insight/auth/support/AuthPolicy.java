package com.demo.insight.auth.support;

import java.util.Locale;
import java.util.Set;

public final class AuthPolicy {

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_USER = "user";
    public static final String USEFLAG_Y = "y";
    public static final String USEFLAG_N = "n";
    public static final String OPERATION_MID_CODE = "mid001";

    private static final Set<String> SUPPORTED_ROLES = Set.of(ROLE_ADMIN, ROLE_USER);
    private static final Set<String> SUPPORTED_USEFLAGS = Set.of(USEFLAG_Y, USEFLAG_N);

    private AuthPolicy() {
    }

    public static String normalizeRole(String role) {
        if (role == null) {
            return "";
        }
        return role.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeUseflag(String useflag) {
        if (useflag == null) {
            return "";
        }
        return useflag.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isSupportedRole(String role) {
        return SUPPORTED_ROLES.contains(normalizeRole(role));
    }

    public static boolean isSupportedUseflag(String useflag) {
        return SUPPORTED_USEFLAGS.contains(normalizeUseflag(useflag));
    }

    public static boolean isAdmin(String role) {
        return ROLE_ADMIN.equals(normalizeRole(role));
    }

    public static boolean isUser(String role) {
        return ROLE_USER.equals(normalizeRole(role));
    }

    public static boolean isActive(String useflag) {
        return USEFLAG_Y.equals(normalizeUseflag(useflag));
    }
}
