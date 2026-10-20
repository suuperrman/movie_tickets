package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.seat.*;
import org.javaup.entity.Seat;
import org.javaup.service.SeatService;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端物理座位接口（/admin/seat）
 *
 * 接口清单：
 *   POST   /admin/seat                  新增
 *   POST   /admin/seat/batch-generate   按影厅批量生成（rowCount × colCount）
 *   PUT    /admin/seat/{id}             修改
 *   DELETE /admin/seat/{id}             删除（被场次引用则拒绝）
 *   GET    /admin/seat/page             分页查询
 *   GET    /admin/seat/{id}             详情
 */
@Slf4j
@RestController
@RequestMapping("/admin/seat")
@RequiredArgsConstructor
public class AdminSeatController {

    private final SeatService seatService;

    @PostMapping
    public Result<Seat> create(@Valid @RequestBody SeatSaveDTO dto) {
        log.info("[Admin] 新增座位 hallId={} row={} col={}", dto.getHallId(), dto.getRowNo(), dto.getColNo());
        return Result.ok(seatService.create(dto));
    }

    @PostMapping("/batch-generate")
    public Result<Integer> batchGenerate(@RequestParam("hallId") Long hallId) {
        log.info("[Admin] 批量生成座位 hallId={}", hallId);
        return Result.ok(seatService.batchGenerate(hallId));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody SeatSaveDTO dto) {
        log.info("[Admin] 修改座位 id={}", id);
        seatService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("[Admin] 删除座位 id={}", id);
        seatService.delete(id);
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<Page<SeatVO>> page(SeatPageQuery query) {
        return Result.ok(seatService.pageQuery(query));
    }

    @GetMapping("/{id}")
    public Result<SeatVO> detail(@PathVariable("id") Long id) {
        return Result.ok(seatService.detail(id));
    }
}
