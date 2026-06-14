package com.demo.insight.auth.service;

import com.demo.insight.auth.domain.EmployeeDocument;
import com.demo.insight.auth.repository.EmployeeRepository;
import com.demo.insight.auth.support.AuthPolicy;
import com.demo.insight.common.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

@Service
public class RequestActorService {

    private final EmployeeRepository employeeRepository;

    public RequestActorService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public AuthenticatedActor resolve(String empcodeHeader, String roleHeader) {
        String normalizedEmpcode = normalizeEmpcode(empcodeHeader);
        String normalizedRole = AuthPolicy.normalizeRole(roleHeader);

        if (normalizedEmpcode.isBlank() || normalizedRole.isBlank()) {
            throw new UnauthorizedException("Authentication context is required.");
        }

        EmployeeDocument employee = employeeRepository.findByEmpcode(normalizedEmpcode)
                .orElseThrow(() -> new UnauthorizedException("Authentication context is unavailable for this demo request."));

        if (!AuthPolicy.isActive(employee.getUseflag())) {
            throw new UnauthorizedException("Inactive account.");
        }

        String storedRole = AuthPolicy.normalizeRole(employee.getRole());
        if (!storedRole.equals(normalizedRole)) {
            throw new UnauthorizedException("Authentication context is unavailable for this demo request.");
        }

        return new AuthenticatedActor(
                employee.getEmpcode(),
                storedRole,
                employee.getEmpname(),
                AuthPolicy.normalizeUseflag(employee.getUseflag())
        );
    }

    private String normalizeEmpcode(String empcode) {
        if (empcode == null) {
            return "";
        }
        return empcode.trim();
    }

    public record AuthenticatedActor(String empcode, String role, String empname, String useflag) {
        public boolean isAdmin() {
            return AuthPolicy.isAdmin(role);
        }
    }
}
