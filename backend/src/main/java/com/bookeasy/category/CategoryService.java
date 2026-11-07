package com.bookeasy.category;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.category.dto.CategoryDtos.CategoryView;
import com.bookeasy.category.dto.CategoryDtos.RequirementView;
import com.bookeasy.category.dto.CategoryDtos.SubcategoryView;
import com.bookeasy.category.entity.Category;
import com.bookeasy.category.entity.DocumentRequirement;
import com.bookeasy.category.entity.Subcategory;
import com.bookeasy.category.repository.CategoryRepository;
import com.bookeasy.category.repository.DocumentRequirementRepository;
import com.bookeasy.category.repository.SubcategoryRepository;

/** Read side of the catalogue: the public tree (active only) and the full admin tree. */
@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final SubcategoryRepository subcategories;
    private final DocumentRequirementRepository requirements;

    public CategoryService(CategoryRepository categories, SubcategoryRepository subcategories,
                           DocumentRequirementRepository requirements) {
        this.categories = categories;
        this.subcategories = subcategories;
        this.requirements = requirements;
    }

    /** Active categories with their active subcategories. Categories without any active subcategory are hidden. */
    @Transactional(readOnly = true)
    public List<CategoryView> publicTree() {
        List<Category> cats = categories.findByActiveTrueOrderBySortOrderAscIdAsc();
        Map<Long, List<SubcategoryView>> subs = subcategories
                .findByCategoryIdInOrderBySortOrderAscIdAsc(cats.stream().map(Category::getId).toList()).stream()
                .filter(Subcategory::isActive)
                .collect(Collectors.groupingBy(Subcategory::getCategoryId,
                        Collectors.mapping(SubcategoryView::of, Collectors.toList())));
        return cats.stream()
                .filter(c -> subs.containsKey(c.getId()))
                .map(c -> CategoryView.of(c, subs.get(c.getId()), null))
                .toList();
    }

    /** Everything, including inactive items and document requirements. */
    @Transactional(readOnly = true)
    public List<CategoryView> adminTree() {
        List<Category> cats = categories.findAllByOrderBySortOrderAscIdAsc();
        List<Long> ids = cats.stream().map(Category::getId).toList();
        Map<Long, List<SubcategoryView>> subs = subcategories.findByCategoryIdInOrderBySortOrderAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(Subcategory::getCategoryId,
                        Collectors.mapping(SubcategoryView::of, Collectors.toList())));
        Map<Long, List<RequirementView>> reqs = requirements.findByCategoryIdInOrderBySortOrderAscIdAsc(ids).stream()
                .collect(Collectors.groupingBy(DocumentRequirement::getCategoryId,
                        Collectors.mapping(RequirementView::of, Collectors.toList())));
        return cats.stream()
                .map(c -> CategoryView.of(c, subs.getOrDefault(c.getId(), List.of()),
                        reqs.getOrDefault(c.getId(), List.of())))
                .toList();
    }
}
