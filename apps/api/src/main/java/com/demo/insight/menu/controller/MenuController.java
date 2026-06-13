package com.demo.insight.menu.controller;

import com.demo.insight.auth.service.RequestActorService;
import com.demo.insight.auth.support.AuthPolicy;
import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.common.exception.ForbiddenException;
import com.demo.insight.menu.dto.MenuResponseDto;
import com.demo.insight.menu.service.MenuService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;
    private final RequestActorService requestActorService;

    public MenuController(MenuService menuService, RequestActorService requestActorService) {
        this.menuService = menuService;
        this.requestActorService = requestActorService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MenuResponseDto>> getMenu(
            @RequestParam("role") String role,
            @RequestHeader(value = "X-Auth-Empcode", required = false) String authEmpcode,
            @RequestHeader(value = "X-Auth-Role", required = false) String authRole
    ) {
        String effectiveRole = role;
        boolean hasHeaderContext = authEmpcode != null || authRole != null;

        if (hasHeaderContext) {
            RequestActorService.AuthenticatedActor actor = requestActorService.resolve(authEmpcode, authRole);
            String requestedRole = AuthPolicy.normalizeRole(role);
            if (!actor.role().equals(requestedRole)) {
                throw new ForbiddenException("Role mismatch.");
            }
            effectiveRole = actor.role();
        }

        MenuResponseDto response = menuService.getMenuByRole(effectiveRole);
        return ResponseEntity.ok(ApiResponse.success(response, "Menu loaded."));
    }
}

