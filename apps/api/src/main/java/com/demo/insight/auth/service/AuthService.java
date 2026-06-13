package com.demo.insight.auth.service;

import com.demo.insight.auth.dto.LoginRequestDto;
import com.demo.insight.auth.dto.LoginResponseDto;

import java.util.Optional;

public interface AuthService {
    Optional<LoginResponseDto> login(LoginRequestDto request);
}

