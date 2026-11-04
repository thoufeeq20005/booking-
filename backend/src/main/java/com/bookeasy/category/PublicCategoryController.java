package com.bookeasy.category;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookeasy.category.dto.CategoryDtos.CategoryView;
import com.bookeasy.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/public/categories")
@Tag(name = "Categories")
@SecurityRequirements
public class PublicCategoryController {

    private final CategoryService service;

    public PublicCategoryController(CategoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Active categories and subcategories, with names in every language")
    public ApiResponse<List<CategoryView>> list() {
        return ApiResponse.ok(service.publicTree());
    }
}
