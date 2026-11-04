package com.bookeasy.category.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.category.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderBySortOrderAscIdAsc();

    List<Category> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsBySlug(String slug);
}
