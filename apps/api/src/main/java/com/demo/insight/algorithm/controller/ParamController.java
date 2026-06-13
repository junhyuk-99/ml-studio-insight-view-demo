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
                        "?뚭퀬由ъ쬁 湲곕낯 ?뚮씪誘명꽣??議고쉶 ?꾩슜?낅땲?? ?뚮씪誘명꽣 蹂寃쎌? 紐⑤뜽?숈뒿 ?뺤콉 ?붾㈃???댁슜?섏꽭??",
                        "ALGORITHM_PARAM_READ_ONLY"
                ));
    }
}