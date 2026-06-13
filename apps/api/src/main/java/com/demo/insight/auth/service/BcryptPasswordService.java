package com.demo.insight.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class BcryptPasswordService implements PasswordService {

    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final List<String> BCRYPT_PREFIXES = List.of("$2a$", "$2b$", "$2y$");

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public PasswordVerificationResult verifyForLogin(String rawPassword, String storedPassword) {
        if (isBcryptHash(storedPassword)) {
            try {
                return new PasswordVerificationResult(passwordEncoder.matches(rawPassword, storedPassword), false);
            } catch (IllegalArgumentException ignored) {
                return new PasswordVerificationResult(false, false);
            }
        }

        boolean matched = Objects.equals(rawPassword, storedPassword);
        return new PasswordVerificationResult(matched, matched);
    }

    @Override
    public boolean matches(String rawPassword, String storedPassword) {
        return verifyForLogin(rawPassword, storedPassword).matched();
    }

    @Override
    public String encode(String rawPassword) {
        validateNewPassword(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public void validateNewPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
    }

    private boolean isBcryptHash(String value) {
        if (value == null) {
            return false;
        }
        return BCRYPT_PREFIXES.stream().anyMatch(value::startsWith);
    }
}
