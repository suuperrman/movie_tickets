package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.review.ReviewCreateDTO;
import org.javaup.dto.review.ReviewVO;
import org.javaup.entity.MovieReview;
import org.javaup.service.MovieReviewService;
import org.javaup.service.RateLimitService;
import org.javaup.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端：电影评价接口
 * <p>注意：/review/** 不在登录拦截白名单内，所有接口均要求登录。</p>
 */
@Slf4j
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class UserReviewController {

    private final MovieReviewService reviewService;
    private final RateLimitService rateLimitService;

    /**
     * 发布评价（限流：60 秒内最多 3 次）
     */
    @PostMapping
    public Result<MovieReview> create(@Valid @RequestBody ReviewCreateDTO dto) {
        Long userId = UserHolder.getUser().getId();
        rateLimitService.tryAcquire("review_create", String.valueOf(userId), 3, 60);
        log.info("[用户端-发布评价] userId={} movieId={} rating={}", userId, dto.getMovieId(), dto.getRating());
        return Result.ok(reviewService.create(userId, dto));
    }

    /**
     * 我的评价列表（含待审核/已拒绝，便于用户查看自己提交的内容）
     */
    @GetMapping("/my")
    public Result<Page<ReviewVO>> myReviews(@RequestParam(defaultValue = "1") Integer pageNo,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(reviewService.myReviews(userId, pageNo, pageSize));
    }

    /**
     * 当前用户对某部电影的评价（未评价返回 data=null）
     */
    @GetMapping("/mine/{movieId}")
    public Result<ReviewVO> myReviewOfMovie(@PathVariable Long movieId) {
        Long userId = UserHolder.getUser().getId();
        return Result.ok(reviewService.myReviewOfMovie(userId, movieId));
    }
}
