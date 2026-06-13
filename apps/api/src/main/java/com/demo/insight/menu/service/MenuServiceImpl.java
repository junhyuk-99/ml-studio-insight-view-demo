package com.demo.insight.menu.service;

import com.demo.insight.auth.support.AuthPolicy;
import com.demo.insight.menu.domain.MenuMidDocument;
import com.demo.insight.menu.domain.MenuProgramDocument;
import com.demo.insight.menu.domain.MenuSubDocument;
import com.demo.insight.menu.dto.MenuMidDto;
import com.demo.insight.menu.dto.MenuProgramDto;
import com.demo.insight.menu.dto.MenuResponseDto;
import com.demo.insight.menu.dto.MenuSubDto;
import com.demo.insight.menu.repository.MenuMidRepository;
import com.demo.insight.menu.repository.MenuProgramRepository;
import com.demo.insight.menu.repository.MenuSubRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuServiceImpl implements MenuService {

    private final MenuMidRepository menuMidRepository;
    private final MenuSubRepository menuSubRepository;
    private final MenuProgramRepository menuProgramRepository;

    public MenuServiceImpl(
            MenuMidRepository menuMidRepository,
            MenuSubRepository menuSubRepository,
            MenuProgramRepository menuProgramRepository
    ) {
        this.menuMidRepository = menuMidRepository;
        this.menuSubRepository = menuSubRepository;
        this.menuProgramRepository = menuProgramRepository;
    }

    @Override
    public MenuResponseDto getMenuByRole(String role) {
        String normalizedRole = AuthPolicy.normalizeRole(role);
        List<String> allowedRoles = resolveAllowedRoles(normalizedRole);
        List<MenuMidDocument> midDocuments = menuMidRepository.findByUseflagOrderBySortnoAsc(AuthPolicy.USEFLAG_Y);
        List<MenuSubDocument> subDocuments = menuSubRepository.findByUseflagOrderBySortnoAsc(AuthPolicy.USEFLAG_Y);
        List<MenuProgramDocument> programDocuments = menuProgramRepository
                .findByUseflagAndRoleInOrderByMidcodeAscSubcodeAscSortnoAsc(AuthPolicy.USEFLAG_Y, allowedRoles);

        Map<String, MenuMidDto> midMap = new LinkedHashMap<>();
        for (MenuMidDocument midDocument : midDocuments) {
            if (isHiddenMidForRole(normalizedRole, midDocument.getMidcode())) {
                continue;
            }

            midMap.put(midDocument.getMidcode(), new MenuMidDto(
                    midDocument.getMidcode(),
                    midDocument.getMidname(),
                    midDocument.getSortno()
            ));
        }

        Map<String, MenuSubDto> subMap = new HashMap<>();
        for (MenuSubDocument subDocument : subDocuments) {
            MenuMidDto midDto = midMap.get(subDocument.getMidcode());
            if (midDto == null) {
                continue;
            }

            MenuSubDto subDto = new MenuSubDto(
                    subDocument.getSubcode(),
                    subDocument.getSubname(),
                    subDocument.getSortno()
            );
            midDto.getSubmenus().add(subDto);
            subMap.put(toSubKey(subDocument.getMidcode(), subDocument.getSubcode()), subDto);
        }

        for (MenuProgramDocument programDocument : programDocuments) {
            MenuMidDto midDto = midMap.get(programDocument.getMidcode());
            if (midDto == null) {
                continue;
            }

            MenuProgramDto programDto = new MenuProgramDto(
                    programDocument.getPgmcode(),
                    programDocument.getPgmname(),
                    programDocument.getPgmpath(),
                    programDocument.getSortno()
            );

            String subcode = programDocument.getSubcode();
            if (subcode == null || subcode.isBlank()) {
                midDto.getPrograms().add(programDto);
                continue;
            }

            MenuSubDto subDto = subMap.get(toSubKey(programDocument.getMidcode(), subcode));
            if (subDto != null) {
                subDto.getPrograms().add(programDto);
            }
        }

        List<MenuMidDto> menus = midMap.values().stream()
                .peek(menu -> menu.getSubmenus().removeIf(submenu -> submenu.getPrograms().isEmpty()))
                .filter(menu -> !menu.getPrograms().isEmpty() || !menu.getSubmenus().isEmpty())
                .toList();

        return new MenuResponseDto(menus);
    }

    private List<String> resolveAllowedRoles(String role) {
        if (AuthPolicy.isAdmin(role)) {
            return List.of(AuthPolicy.ROLE_ADMIN, AuthPolicy.ROLE_USER);
        }
        if (AuthPolicy.isUser(role)) {
            return List.of(AuthPolicy.ROLE_USER);
        }
        throw new IllegalArgumentException("Unsupported role: " + role);
    }

    private boolean isHiddenMidForRole(String role, String midcode) {
        return AuthPolicy.isUser(role) && AuthPolicy.OPERATION_MID_CODE.equals(midcode);
    }

    private String toSubKey(String midcode, String subcode) {
        return midcode + "::" + subcode;
    }
}

