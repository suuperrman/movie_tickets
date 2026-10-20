package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.actor.*;
import org.javaup.entity.Actor;
import org.javaup.service.ActorService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端演员接口（/admin/actor）
 *
 * 接口清单：
 *   POST   /admin/actor                 新增
 *   PUT    /admin/actor/{id}            修改
 *   DELETE /admin/actor/{id}            删除（被电影引用则拒绝）
 *   PATCH  /admin/actor/{id}/status     启停用
 *   GET    /admin/actor/page            分页查询
 *   GET    /admin/actor/{id}            详情
 *
 * 分页参数 pageNo/pageSize 由 ActorPageQuery 继承自 movie-common 的 BasePageQuery。
 */
@Slf4j
@RestController
@RequestMapping("/admin/actor")
@RequiredArgsConstructor
public class AdminActorController {

    private final ActorService actorService;

    // ==================== 1. 新增 ====================

    @PostMapping
    public Result<Actor> create(@Valid @RequestBody ActorSaveDTO dto) {
        log.info("[Admin] 新增演员 name={}", dto.getName());
        return Result.ok(actorService.create(dto));
    }

    // ==================== 2. 修改 ====================

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody ActorSaveDTO dto) {
        log.info("[Admin] 修改演员 id={}", id);
        actorService.update(id, dto);
        return Result.ok();
    }

    // ==================== 3. 删除 ====================

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("[Admin] 删除演员 id={}", id);
        actorService.delete(id);
        return Result.ok();
    }

    // ==================== 4. 启停用 ====================

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        log.info("[Admin] 演员启停用 id={} status={}", id, status);
        actorService.updateStatus(id, status);
        return Result.ok();
    }

    // ==================== 5. 分页查询 ====================

    @GetMapping("/page")
    public Result<Page<ActorVO>> page(ActorPageQuery query) {
        return Result.ok(actorService.pageQuery(query));
    }

    // ==================== 6. 详情 ====================

    @GetMapping("/{id}")
    public Result<ActorVO> detail(@PathVariable("id") Long id) {
        return Result.ok(actorService.detail(id));
    }
}
