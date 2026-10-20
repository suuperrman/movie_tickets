package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.hall.*;
import org.javaup.entity.Hall;
import org.javaup.service.HallService;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端影厅接口（/admin/hall）
 *
 * 接口清单：
 *   POST   /admin/hall                 新增
 *   PUT    /admin/hall/{id}            修改
 *   DELETE /admin/hall/{id}            删除（有排片则拒绝）
 *   GET    /admin/hall/page            分页查询
 *   GET    /admin/hall/{id}            详情
 *
 * 影厅无独立启停用，随所属影院状态联动。
 */
@Slf4j
@RestController
@RequestMapping("/admin/hall")
@RequiredArgsConstructor
public class AdminHallController {

    private final HallService hallService;

    @PostMapping
    public Result<Hall> create(@Valid @RequestBody HallSaveDTO dto) {
        log.info("[Admin] 新增影厅 cinemaId={} name={}", dto.getCinemaId(), dto.getName());
        return Result.ok(hallService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody HallSaveDTO dto) {
        log.info("[Admin] 修改影厅 id={}", id);
        hallService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("[Admin] 删除影厅 id={}", id);
        hallService.delete(id);
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<Page<HallVO>> page(HallPageQuery query) {
        return Result.ok(hallService.pageQuery(query));
    }

    @GetMapping("/{id}")
    public Result<HallVO> detail(@PathVariable("id") Long id) {
        return Result.ok(hallService.detail(id));
    }
}
