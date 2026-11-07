package com.bookeasy.category;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.category.dto.CategoryDtos.CategoryRequest;
import com.bookeasy.category.dto.CategoryDtos.CategoryView;
import com.bookeasy.category.dto.CategoryDtos.RequirementRequest;
import com.bookeasy.category.dto.CategoryDtos.SubcategoryRequest;
import com.bookeasy.common.api.ApiResponse;
import com.bookeasy.common.security.CurrentUser;
import com.bookeasy.common.web.ClientIpResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Catalogue management. Every mutation returns the full admin tree. SUPER_ADMIN only (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin/categories")
@Tag(name = "Admin – Categories")
public class AdminCategoryController {

    private final CategoryService categoryService;
    private final AdminCategoryService service;
    private final ClientIpResolver ip;

    public AdminCategoryController(CategoryService categoryService, AdminCategoryService service,
                                   ClientIpResolver ip) {
        this.categoryService = categoryService;
        this.service = service;
        this.ip = ip;
    }

    @GetMapping
    @Operation(summary = "All categories, including inactive ones, with subcategories and document requirements")
    public ApiResponse<List<CategoryView>> list() {
        return ApiResponse.ok(categoryService.adminTree());
    }

    @PostMapping
    public ApiResponse<List<CategoryView>> create(@Valid @RequestBody CategoryRequest req, HttpServletRequest http) {
        return ApiResponse.ok(service.createCategory(CurrentUser.require(), req, ip.resolve(http)));
    }

    @PutMapping("/{id}")
    public ApiResponse<List<CategoryView>> update(@PathVariable long id, @Valid @RequestBody CategoryRequest req,
                                                  HttpServletRequest http) {
        return ApiResponse.ok(service.updateCategory(CurrentUser.require(), id, req, ip.resolve(http)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category nobody uses (otherwise deactivate it)")
    public ApiResponse<List<CategoryView>> delete(@PathVariable long id, HttpServletRequest http) {
        return ApiResponse.ok(service.deleteCategory(CurrentUser.require(), id, ip.resolve(http)));
    }

    @PostMapping("/{categoryId}/subcategories")
    public ApiResponse<List<CategoryView>> createSubcategory(@PathVariable long categoryId,
                                                             @Valid @RequestBody SubcategoryRequest req,
                                                             HttpServletRequest http) {
        return ApiResponse.ok(service.createSubcategory(CurrentUser.require(), categoryId, req, ip.resolve(http)));
    }

    @PutMapping("/{categoryId}/subcategories/{id}")
    public ApiResponse<List<CategoryView>> updateSubcategory(@PathVariable long categoryId, @PathVariable long id,
                                                             @Valid @RequestBody SubcategoryRequest req,
                                                             HttpServletRequest http) {
        return ApiResponse.ok(service.updateSubcategory(CurrentUser.require(), categoryId, id, req, ip.resolve(http)));
    }

    @DeleteMapping("/{categoryId}/subcategories/{id}")
    public ApiResponse<List<CategoryView>> deleteSubcategory(@PathVariable long categoryId, @PathVariable long id,
                                                             HttpServletRequest http) {
        return ApiResponse.ok(service.deleteSubcategory(CurrentUser.require(), categoryId, id, ip.resolve(http)));
    }

    @PostMapping("/{categoryId}/document-requirements")
    public ApiResponse<List<CategoryView>> createRequirement(@PathVariable long categoryId,
                                                             @Valid @RequestBody RequirementRequest req,
                                                             HttpServletRequest http) {
        return ApiResponse.ok(service.createRequirement(CurrentUser.require(), categoryId, req, ip.resolve(http)));
    }

    @PutMapping("/{categoryId}/document-requirements/{id}")
    public ApiResponse<List<CategoryView>> updateRequirement(@PathVariable long categoryId, @PathVariable long id,
                                                             @Valid @RequestBody RequirementRequest req,
                                                             HttpServletRequest http) {
        return ApiResponse.ok(service.updateRequirement(CurrentUser.require(), categoryId, id, req, ip.resolve(http)));
    }

    @DeleteMapping("/{categoryId}/document-requirements/{id}")
    public ApiResponse<List<CategoryView>> deleteRequirement(@PathVariable long categoryId, @PathVariable long id,
                                                             HttpServletRequest http) {
        return ApiResponse.ok(service.deleteRequirement(CurrentUser.require(), categoryId, id, ip.resolve(http)));
    }
}
