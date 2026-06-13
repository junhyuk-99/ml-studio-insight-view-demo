package com.demo.insight.menu.repository;

import com.demo.insight.menu.domain.MenuProgramDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MenuProgramRepository extends MongoRepository<MenuProgramDocument, String> {
    List<MenuProgramDocument> findByUseflagAndRoleInOrderByMidcodeAscSubcodeAscSortnoAsc(String useflag, List<String> roles);
}

