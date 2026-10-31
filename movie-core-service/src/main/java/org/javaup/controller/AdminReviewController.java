package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.review.ReviewPageQuery;
import org.javaup.dto.review.ReviewStatusDTO;
import org.javaup.dto.review.ReviewVO;
import org.javaup.service.MovieReviewService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端：电影评价审核接口
 * <p>由 AdminInterceptor 保护 /admin/** 路径，仅管理员可访问。</p>
 */
@Slf4j
@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
public class AdminReviewController {

    private final MovieReviewService reviewService;

    /**
     * 评价分页查询（支持电影ID、审核状态、评分、内容关键字过滤）
     */
    @GetMapping("/page")
    public Result<Page<ReviewVO>> page(@Valid ReviewPageQuery query) {
        return Result.ok(reviewService.adminPage(query));
    }

    /**
     * 审核评价：0待审核 1已通过 2已拒绝
     */
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody ReviewStatusDTO dto) {
        log.info("[管理端-评价审核] id={} status={}", id, dto.getStatus());
        reviewService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    /**
     * 删除评价（违规内容下架）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("[管理端-删除评价] id={}", id);
        reviewService.deleteReview(id);
        return Result.ok();
    }
}
