package com.demo.insight.algorithm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.demo.insight.algorithm.dto.AlgorithmParamDto;
import com.demo.insight.algorithm.service.ParamService;
import com.demo.insight.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/algorithm")
public class ParamController {

    private final ParamService paramService;

    public ParamController(ParamService paramService) {
        this.paramService = paramService;
    }

    @GetMapping("/params")
    public ResponseEntity<ApiResponse<List<AlgorithmParamDto>>> getParams(
            @RequestParam("algoCd") String algoCd
    ) {
        List<AlgorithmParamDto> data = paramService.getParamsByAlgoCd(algoCd);
        return ResponseEntity.ok(ApiResponse.success(data, "Algorithm parameters loaded."));
    }

    @PutMapping("/params")
    public ResponseEntity<ApiResponse<Void>> saveParams() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(
                        "Algorithm parameters are read-only in this public demo. Use the model training policy page to review demo settings.",
                        "ALGORITHM_PARAM_READ_ONLY"
                ));
    }
}
