package org.javaup.controller;

import lombok.RequiredArgsConstructor;
import org.javaup.dto.Result;
import org.javaup.entity.MovieCategory;
import org.javaup.service.MovieCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端 - 电影分类接口
 */
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final MovieCategoryService categoryService;

    /**
     * 分类列表（仅上架的，按排序；走 Redis 缓存，TTL 1h）
     * GET /category/list
     */
    @GetMapping("/list")
    public Result<List<MovieCategory>> list() {
        return Result.ok(categoryService.listForUser());
    }
}
