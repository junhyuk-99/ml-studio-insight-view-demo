package com.demo.insight.auth.service;

public interface PasswordService {

    PasswordVerificationResult verifyForLogin(String rawPassword, String storedPassword);

    boolean matches(String rawPassword, String storedPassword);

    String encode(String rawPassword);

    void validateNewPassword(String rawPassword);

    record PasswordVerificationResult(boolean matched, boolean legacyMatched) {
    }
}
