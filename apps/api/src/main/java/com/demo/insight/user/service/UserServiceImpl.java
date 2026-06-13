package com.demo.insight.user.service;

import com.demo.insight.auth.domain.EmployeeDocument;
import com.demo.insight.auth.repository.EmployeeRepository;
import com.demo.insight.auth.service.PasswordService;
import com.demo.insight.auth.service.RequestActorService;
import com.demo.insight.auth.support.AuthPolicy;
import com.demo.insight.common.exception.ForbiddenException;
import com.demo.insight.user.dto.ChangePasswordRequestDto;
import com.demo.insight.user.dto.CreateUserRequestDto;
import com.demo.insight.user.dto.UpdateUserRequestDto;
import com.demo.insight.user.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final EmployeeRepository employeeRepository;
    private final PasswordService passwordService;

    public UserServiceImpl(EmployeeRepository employeeRepository, PasswordService passwordService) {
        this.employeeRepository = employeeRepository;
        this.passwordService = passwordService;
    }

    @Override
    public List<UserDto> getUsers(RequestActorService.AuthenticatedActor actor) {
        requireAdmin(actor);
        return employeeRepository.findAllByOrderByEmpcodeAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public UserDto getUser(String empcode, RequestActorService.AuthenticatedActor actor) {
        String normalizedEmpcode = normalizeEmpcode(empcode);

        if (!actor.isAdmin() && !actor.empcode().equals(normalizedEmpcode)) {
            throw new ForbiddenException("You can view only your own profile.");
        }

        EmployeeDocument employee = findByEmpcode(normalizedEmpcode);
        return toDto(employee);
    }

    @Override
    public UserDto createUser(CreateUserRequestDto request, RequestActorService.AuthenticatedActor actor) {
        requireAdmin(actor);

        String normalizedEmpcode = normalizeEmpcode(request.empcode());
        if (employeeRepository.existsByEmpcode(normalizedEmpcode)) {
            throw new IllegalArgumentException("empcode already exists.");
        }

        String normalizedRole = validateRole(request.role());
        String normalizedUseflag = validateUseflag(request.useflag());
        String empname = normalizeRequiredValue(request.empname(), "empname");

        EmployeeDocument employee = new EmployeeDocument();
        employee.setEmpcode(normalizedEmpcode);
        employee.setEmpname(empname);
        employee.setRole(normalizedRole);
        employee.setUseflag(normalizedUseflag);
        employee.setEmppass(passwordService.encode(request.emppass()));

        return toDto(employeeRepository.save(employee));
    }

    @Override
    public UserDto updateUser(String empcode, UpdateUserRequestDto request, RequestActorService.AuthenticatedActor actor) {
        requireAdmin(actor);

        EmployeeDocument employee = findByEmpcode(normalizeEmpcode(empcode));
        employee.setEmpname(normalizeRequiredValue(request.empname(), "empname"));
        employee.setRole(validateRole(request.role()));
        employee.setUseflag(validateUseflag(request.useflag()));

        return toDto(employeeRepository.save(employee));
    }

    @Override
    public void deleteUser(String empcode, RequestActorService.AuthenticatedActor actor) {
        requireAdmin(actor);

        String normalizedEmpcode = normalizeEmpcode(empcode);
        if (actor.empcode().equals(normalizedEmpcode)) {
            throw new IllegalArgumentException("You cannot delete your own account.");
        }

        EmployeeDocument employee = findByEmpcode(normalizedEmpcode);
        String targetRole = AuthPolicy.normalizeRole(employee.getRole());
        String targetUseflag = AuthPolicy.normalizeUseflag(employee.getUseflag());

        if (AuthPolicy.ROLE_ADMIN.equals(targetRole) && AuthPolicy.USEFLAG_Y.equals(targetUseflag)) {
            long activeAdminCount = employeeRepository.countByRoleAndUseflag(AuthPolicy.ROLE_ADMIN, AuthPolicy.USEFLAG_Y);
            if (activeAdminCount <= 1) {
                throw new IllegalArgumentException("The last active admin cannot be deleted.");
            }
        }

        employeeRepository.delete(employee);
    }

    @Override
    public void changePassword(String empcode, ChangePasswordRequestDto request, RequestActorService.AuthenticatedActor actor) {
        String normalizedEmpcode = normalizeEmpcode(empcode);
        if (!actor.empcode().equals(normalizedEmpcode)) {
            throw new ForbiddenException("You can change only your own password.");
        }

        EmployeeDocument employee = employeeRepository.findByEmpcodeAndUseflag(normalizedEmpcode, AuthPolicy.USEFLAG_Y)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        if (!passwordService.matches(request.currentPassword(), employee.getEmppass())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        passwordService.validateNewPassword(request.newPassword());
        employee.setEmppass(passwordService.encode(request.newPassword()));
        employeeRepository.save(employee);
    }

    private void requireAdmin(RequestActorService.AuthenticatedActor actor) {
        if (!actor.isAdmin()) {
            throw new ForbiddenException("Admin access is required.");
        }
    }

    private EmployeeDocument findByEmpcode(String empcode) {
        return employeeRepository.findByEmpcode(empcode)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private UserDto toDto(EmployeeDocument employee) {
        return new UserDto(
                employee.getEmpcode(),
                employee.getEmpname(),
                AuthPolicy.normalizeRole(employee.getRole()),
                AuthPolicy.normalizeUseflag(employee.getUseflag())
        );
    }

    private String normalizeEmpcode(String empcode) {
        String normalized = normalizeRequiredValue(empcode, "empcode");
        return normalized;
    }

    private String validateRole(String role) {
        String normalizedRole = AuthPolicy.normalizeRole(role);
        if (!AuthPolicy.isSupportedRole(normalizedRole)) {
            throw new IllegalArgumentException("role must be admin or user.");
        }
        return normalizedRole;
    }

    private String validateUseflag(String useflag) {
        String normalizedUseflag = AuthPolicy.normalizeUseflag(useflag);
        if (!AuthPolicy.isSupportedUseflag(normalizedUseflag)) {
            throw new IllegalArgumentException("useflag must be y or n.");
        }
        return normalizedUseflag;
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}
