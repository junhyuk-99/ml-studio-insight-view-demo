package com.demo.insight.equipment.controller;

import com.demo.insight.common.dto.ApiResponse;
import com.demo.insight.equipment.dto.EquipmentMasterDto;
import com.demo.insight.equipment.service.EquipmentMasterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentMasterController {

    private final EquipmentMasterService equipmentMasterService;

    public EquipmentMasterController(EquipmentMasterService equipmentMasterService) {
        this.equipmentMasterService = equipmentMasterService;
    }

    @GetMapping("/master")
    public ResponseEntity<ApiResponse<List<EquipmentMasterDto>>> getEquipmentMaster(
            @RequestParam(name = "ai_only", required = false, defaultValue = "true") boolean aiOnly
    ) {
        List<EquipmentMasterDto> data = aiOnly
                ? equipmentMasterService.getAiTargetEquipments()
                : equipmentMasterService.getActiveEquipments();
        return ResponseEntity.ok(ApiResponse.success(data, "Equipment master loaded."));
    }
}
