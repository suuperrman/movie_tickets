package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.movie.*;
import org.javaup.entity.Movie;
import org.javaup.service.MovieService;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端电影接口（/admin/movie）
 *
 * 对应 README.md 管理端 6 个接口：
 *   POST   /admin/movie                 新增
 *   PUT    /admin/movie/{id}            修改
 *   DELETE /admin/movie/{id}            删除（有场次拒绝）
 *   PATCH  /admin/movie/{id}/status     上下架
 *   GET    /admin/movie/page            分页查询
 *   GET    /admin/movie/{id}            详情
 */
@Slf4j
@RestController
@RequestMapping("/admin/movie")
@RequiredArgsConstructor
public class AdminMovieController {

    private final MovieService movieService;

    // ==================== 1. 新增 ====================

    @PostMapping
    public Result<Movie> create(@Valid @RequestBody MovieSaveDTO dto) {
        log.info("[Admin] 新增电影 name={}", dto.getName());
        return Result.ok(movieService.saveMovie(dto));
    }

    // ==================== 2. 修改 ====================

    @PutMapping("/{id:\\d+}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody MovieSaveDTO dto) {
        log.info("[Admin] 修改电影 id={}", id);
        movieService.updateMovie(id, dto);
        return Result.ok();
    }

    // ==================== 3. 删除 ====================

    @DeleteMapping("/{id:\\d+}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("[Admin] 删除电影 id={}", id);
        movieService.deleteMovie(id);
        return Result.ok();
    }

    // ==================== 4. 上下架 ====================

    @PatchMapping("/{id:\\d+}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @Valid @RequestBody MovieStatusDTO dto) {
        log.info("[Admin] 电影上下架 id={} status={}", id, dto.getStatus());
        movieService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    // ==================== 5. 分页查询 ====================

    @GetMapping("/page")
    public Result<Page<MovieListItemVO>> page(@Valid MoviePageQuery query) {
        return Result.ok(movieService.pageQuery(query));
    }

    // ==================== 6. 详情 ====================

    @GetMapping("/{id:\\d+}")
    public Result<MovieDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(movieService.detailForAdmin(id));
    }
}
