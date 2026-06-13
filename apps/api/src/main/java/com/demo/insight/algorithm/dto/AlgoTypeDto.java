package com.demo.insight.algorithm.dto;

public record AlgoTypeDto(
        String algoTypeCd,
        String algoTypeNm,
        String desc,
        Integer sortOrd
) {
}
