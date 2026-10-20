package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.schedule.*;
import org.javaup.entity.Schedule;
import org.javaup.service.ScheduleService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端场次接口（/admin/schedule）
 *
 * 接口清单：
 *   POST   /admin/schedule                 新增
 *   PUT    /admin/schedule/{id}            修改
 *   DELETE /admin/schedule/{id}            删除（有座位锁定/售票则拒绝）
 *   PATCH  /admin/schedule/{id}/status     启停用
 *   GET    /admin/schedule/page            分页查询
 *   GET    /admin/schedule/{id}            详情
 */
@Slf4j
@RestController
@RequestMapping("/admin/schedule")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public Result<Schedule> create(@Valid @RequestBody ScheduleSaveDTO dto) {
        log.info("[Admin] 新增场次 movieId={} hallId={}", dto.getMovieId(), dto.getHallId());
        return Result.ok(scheduleService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody ScheduleSaveDTO dto) {
        log.info("[Admin] 修改场次 id={}", id);
        scheduleService.update(id, dto);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("[Admin] 删除场次 id={}", id);
        scheduleService.delete(id);
        return Result.ok();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @RequestBody Map<String, Integer> body) {
        log.info("[Admin] 场次启停用 id={}", id);
        scheduleService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<Page<ScheduleAdminVO>> page(SchedulePageQuery query) {
        return Result.ok(scheduleService.pageQuery(query));
    }

    @GetMapping("/{id}")
    public Result<ScheduleAdminVO> detail(@PathVariable("id") Long id) {
        return Result.ok(scheduleService.detail(id));
    }
}
