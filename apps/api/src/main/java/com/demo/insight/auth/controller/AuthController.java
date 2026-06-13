package com.demo.insight.auth.controller;

import com.demo.insight.auth.dto.LoginRequestDto;
import com.demo.insight.auth.dto.LoginResponseDto;
import com.demo.insight.auth.service.AuthService;
import com.demo.insight.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {
        Optional<LoginResponseDto> loginResponse = authService.login(request);

        if (loginResponse.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("Invalid credentials or inactive account.", "AUTH_LOGIN_FAILED"));
        }

        return ResponseEntity.ok(ApiResponse.success(loginResponse.get(), "Login successful."));
    }
}

