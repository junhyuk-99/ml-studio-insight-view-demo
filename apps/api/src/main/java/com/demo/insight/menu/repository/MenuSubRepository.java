package com.demo.insight.menu.repository;

import com.demo.insight.menu.domain.MenuSubDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MenuSubRepository extends MongoRepository<MenuSubDocument, String> {
    List<MenuSubDocument> findByUseflagOrderBySortnoAsc(String useflag);
}

