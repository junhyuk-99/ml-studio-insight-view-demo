package com.demo.insight.user.controller;

import com.demo.insight.auth.service.RequestActorService;
import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.user.dto.ChangePasswordRequestDto;
import com.demo.insight.user.dto.CreateUserRequestDto;
import com.demo.insight.user.dto.UpdateUserRequestDto;
import com.demo.insight.user.dto.UserDto;
import com.demo.insight.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final RequestActorService requestActorService;

    public UserController(UserService userService, RequestActorService requestActorService) {
        this.userService = userService;
        this.requestActorService = requestActorService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getUsers(
            @RequestHeader("X-Auth-Empcode") String authEmpcode,
            @RequestHeader("X-Auth-Role") String authRole
    ) {
        RequestActorService.AuthenticatedActor actor = requestActorService.resolve(authEmpcode, authRole);
        List<UserDto> users = userService.getUsers(actor);
        return ResponseEntity.ok(ApiResponse.success(users, "Users loaded."));
    }

    @GetMapping("/{empcode}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(
            @PathVariable("empcode") String empcode,
            @RequestHeader("X-Auth-Empcode") String authEmpcode,
            @RequestHeader("X-Auth-Role") String authRole
    ) {
        RequestActorService.AuthenticatedActor actor = requestActorService.resolve(authEmpcode, authRole);
        UserDto user = userService.getUser(empcode, actor);
        return ResponseEntity.ok(ApiResponse.success(user, "User loaded."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDto>> createUser(
            @Valid @RequestBody CreateUserRequestDto request,
            @RequestHeader("X-Auth-Empcode") String authEmpcode,
            @RequestHeader("X-Auth-Role") String authRole
    ) {
        RequestActorService.AuthenticatedActor actor = requestActorService.resolve(authEmpcode, authRole);
        UserDto user = userService.createUser(request, actor);
        return ResponseEntity.ok(ApiResponse.success(user, "User created."));
    }

    @PutMapping("/{empcode}")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(
            @PathVariable("empcode") String empcode,
            @Valid @RequestBody UpdateUserRequestDto request,
            @RequestHeader("X-Auth-Empcode") String authEmpcode,
            @RequestHeader("X-Auth-Role") String authRole
    ) {
        RequestActorService.AuthenticatedActor actor = requestActorService.resolve(authEmpcode, authRole);
        UserDto user = userService.updateUser(empcode, request, actor);
        return ResponseEntity.ok(ApiResponse.success(user, "User updated."));
    }

    @DeleteMapping("/{empcode}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable("empcode") String empcode,
            @RequestHeader("X-Auth-Empcode") String authEmpcode,
            @RequestHeader("X-Auth-Role") String authRole
    ) {
        RequestActorService.AuthenticatedActor actor = requestActorService.resolve(authEmpcode, authRole);
        userService.deleteUser(empcode, actor);
        return ResponseEntity.ok(ApiResponse.success(null, "User deleted."));
    }

    @PutMapping("/{empcode}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable("empcode") String empcode,
            @Valid @RequestBody ChangePasswordRequestDto request,
            @RequestHeader("X-Auth-Empcode") String authEmpcode,
            @RequestHeader("X-Auth-Role") String authRole
    ) {
        RequestActorService.AuthenticatedActor actor = requestActorService.resolve(authEmpcode, authRole);
        userService.changePassword(empcode, request, actor);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed."));
    }
}
