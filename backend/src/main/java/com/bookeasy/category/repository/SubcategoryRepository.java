package com.bookeasy.category.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.category.entity.Subcategory;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {

    List<Subcategory> findByCategoryIdInOrderBySortOrderAscIdAsc(Collection<Long> categoryIds);

    List<Subcategory> findByCategoryId(Long categoryId);

    boolean existsByCategoryIdAndSlug(Long categoryId, String slug);

    boolean existsByCategoryIdAndSlugAndIdNot(Long categoryId, String slug, Long id);
}
