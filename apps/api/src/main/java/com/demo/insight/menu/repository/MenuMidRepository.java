package com.demo.insight.menu.repository;

import com.demo.insight.menu.domain.MenuMidDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MenuMidRepository extends MongoRepository<MenuMidDocument, String> {
    List<MenuMidDocument> findByUseflagOrderBySortnoAsc(String useflag);
}

