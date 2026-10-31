package org.javaup.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.entity.MovieCategory;
import org.javaup.service.MovieCategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端 - 电影分类接口
 */
@Slf4j
@RestController
@RequestMapping("/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final MovieCategoryService categoryService;

    /**
     * 分类列表（全部上架的，用于下拉选择）
     * GET /admin/category/list
     */
    @GetMapping("/list")
    public Result<List<MovieCategory>> list() {
        return Result.ok(categoryService.listForUser());
    }

    /**
     * 全部分类（管理列表用，实时数据）
     * GET /admin/category/all
     */
    @GetMapping("/all")
    public Result<List<MovieCategory>> all() {
        return Result.ok(categoryService.listAll());
    }

    /**
     * 新增分类
     * POST /admin/category
     */
    @PostMapping
    public Result<MovieCategory> create(@RequestBody MovieCategory category) {
        return Result.ok(categoryService.createCategory(category));
    }

    /**
     * 修改分类
     * PUT /admin/category/{id}
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody MovieCategory category) {
        categoryService.updateCategory(id, category);
        return Result.ok();
    }

    /**
     * 删除分类
     * DELETE /admin/category/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.ok();
    }
}
