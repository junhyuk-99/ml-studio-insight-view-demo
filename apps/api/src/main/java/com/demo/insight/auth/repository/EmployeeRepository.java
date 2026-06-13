package com.demo.insight.auth.repository;

import com.demo.insight.auth.domain.EmployeeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends MongoRepository<EmployeeDocument, String> {
    Optional<EmployeeDocument> findByEmpcode(String empcode);

    Optional<EmployeeDocument> findByEmpcodeAndUseflag(String empcode, String useflag);

    boolean existsByEmpcode(String empcode);

    List<EmployeeDocument> findAllByOrderByEmpcodeAsc();

    long countByRoleAndUseflag(String role, String useflag);
}

