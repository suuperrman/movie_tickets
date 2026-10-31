package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.review.MovieRatingVO;
import org.javaup.dto.review.ReviewCreateDTO;
import org.javaup.dto.review.ReviewPageQuery;
import org.javaup.dto.review.ReviewVO;
import org.javaup.entity.MovieReview;

import java.util.Collection;
import java.util.Map;

/**
 * 电影评价 / 评分服务
 */
public interface MovieReviewService extends IService<MovieReview> {

    /**
     * 用户发布评价（校验购票资格 + 防重复评价）
     */
    MovieReview create(Long userId, ReviewCreateDTO dto);

    /**
     * 用户端：某部电影的评价列表（仅已通过审核）
     */
    Page<ReviewVO> pageByMovie(Long movieId, Integer pageNo, Integer pageSize);

    /**
     * 单部电影评分聚合（平均分 + 评价数）
     */
    MovieRatingVO movieRating(Long movieId);

    /**
     * 批量电影评分聚合（列表页使用，一次 SQL 解决 N+1）
     */
    Map<Long, MovieRatingVO> batchRatings(Collection<Long> movieIds);

    /**
     * 用户端：我的评价列表
     */
    Page<ReviewVO> myReviews(Long userId, Integer pageNo, Integer pageSize);

    /**
     * 当前用户对某部电影的评价（未评价返回 null）
     */
    ReviewVO myReviewOfMovie(Long userId, Long movieId);

    /**
     * 管理端：评价分页查询
     */
    Page<ReviewVO> adminPage(ReviewPageQuery query);

    /**
     * 管理端：审核评价（0待审核 1已通过 2已拒绝）
     */
    void updateStatus(Long id, Integer status);

    /**
     * 管理端：删除评价
     */
    void deleteReview(Long id);
}
