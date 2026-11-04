package com.bookeasy.category.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.category.entity.DocumentRequirement;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Long> {

    List<DocumentRequirement> findByCategoryIdInOrderBySortOrderAscIdAsc(Collection<Long> categoryIds);

    List<DocumentRequirement> findByCategoryIdOrderBySortOrderAscIdAsc(Long categoryId);

    boolean existsByCategoryIdAndCode(Long categoryId, String code);

    boolean existsByCategoryIdAndCodeAndIdNot(Long categoryId, String code, Long id);
}
