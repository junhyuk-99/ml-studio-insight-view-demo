package com.demo.insight.auth.service;

import com.demo.insight.auth.domain.EmployeeDocument;
import com.demo.insight.auth.dto.LoginRequestDto;
import com.demo.insight.auth.dto.LoginResponseDto;
import com.demo.insight.auth.repository.EmployeeRepository;
import com.demo.insight.auth.support.AuthPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final EmployeeRepository employeeRepository;
    private final PasswordService passwordService;

    public AuthServiceImpl(EmployeeRepository employeeRepository, PasswordService passwordService) {
        this.employeeRepository = employeeRepository;
        this.passwordService = passwordService;
    }

    @Override
    public Optional<LoginResponseDto> login(LoginRequestDto request) {
        Optional<EmployeeDocument> employeeOptional = employeeRepository.findByEmpcode(request.empcode());

        if (employeeOptional.isEmpty()) {
            return Optional.empty();
        }

        EmployeeDocument employee = employeeOptional.get();
        if (!AuthPolicy.isActive(employee.getUseflag())) {
            return Optional.empty();
        }

        String normalizedRole = AuthPolicy.normalizeRole(employee.getRole());
        if (!AuthPolicy.isSupportedRole(normalizedRole)) {
            return Optional.empty();
        }

        PasswordService.PasswordVerificationResult verificationResult = passwordService
                .verifyForLogin(request.emppass(), employee.getEmppass());

        if (!verificationResult.matched()) {
            return Optional.empty();
        }

        if (verificationResult.legacyMatched()) {
            employee.setEmppass(passwordService.encode(request.emppass()));
            employeeRepository.save(employee);
            log.info("legacy password upgraded for empcode={}", employee.getEmpcode());
        }

        return Optional.of(new LoginResponseDto(
                employee.getEmpcode(),
                employee.getEmpname(),
                normalizedRole,
                AuthPolicy.normalizeUseflag(employee.getUseflag())
        ));
    }
}

