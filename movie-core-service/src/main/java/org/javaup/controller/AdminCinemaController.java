package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.cinema.*;
import org.javaup.entity.Cinema;
import org.javaup.service.CinemaService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端影院接口（/admin/cinema）
 *
 * 接口清单：
 *   POST   /admin/cinema                 新增
 *   PUT    /admin/cinema/{id}            修改
 *   DELETE /admin/cinema/{id}            删除（有影厅/场次则拒绝）
 *   PATCH  /admin/cinema/{id}/status     启停用
 *   GET    /admin/cinema/page            分页查询
 *   GET    /admin/cinema/{id}            详情
 */
@Slf4j
@RestController
@RequestMapping("/admin/cinema")
@RequiredArgsConstructor
public class AdminCinemaController {

    private final CinemaService cinemaService;

    @PostMapping
    public Result<Cinema> create(@Valid @RequestBody CinemaSaveDTO dto) {
        log.info("[Admin] 新增影院 name={}", dto.getName());
        return Result.ok(cinemaService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody CinemaSaveDTO dto) {
        log.info("[Admin] 修改影院 id={}", id);
        cinemaService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("[Admin] 删除影院 id={}", id);
        cinemaService.delete(id);
        return Result.ok();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @RequestBody Map<String, Integer> body) {
        log.info("[Admin] 影院启停用 id={}", id);
        cinemaService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<Page<CinemaVO>> page(CinemaPageQuery query) {
        return Result.ok(cinemaService.pageQuery(query));
    }

    @GetMapping("/{id}")
    public Result<CinemaVO> detail(@PathVariable("id") Long id) {
        return Result.ok(cinemaService.detail(id));
    }
}
