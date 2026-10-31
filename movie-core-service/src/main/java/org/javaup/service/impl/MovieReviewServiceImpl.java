package org.javaup.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.review.MovieRatingVO;
import org.javaup.dto.review.ReviewCreateDTO;
import org.javaup.dto.review.ReviewPageQuery;
import org.javaup.dto.review.ReviewVO;
import org.javaup.entity.Movie;
import org.javaup.entity.MovieReview;
import org.javaup.entity.Order;
import org.javaup.entity.SysUser;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.MovieMapper;
import org.javaup.mapper.MovieReviewMapper;
import org.javaup.mapper.OrderMapper;
import org.javaup.mapper.SysUserMapper;
import org.javaup.service.MovieReviewService;
import org.javaup.utils.MovieCacheEvictHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 电影评价 / 评分服务实现
 *
 * <p>业务规则：</p>
 * <ul>
 *     <li>只有购买并支付过该电影的用户才能评价（防止刷好评/恶意差评）</li>
 *     <li>同一用户对同一部电影只能评价一次（DB 唯一索引 + 业务校验双保险）</li>
 *     <li>采用「先发后审」：发布即已通过（status=1），管理端可事后拒绝或删除</li>
 *     <li>评分聚合结果内嵌到电影详情/列表 VO 中，评价写操作后主动失效电影缓存</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieReviewServiceImpl extends ServiceImpl<MovieReviewMapper, MovieReview> implements MovieReviewService {

    /** 审核状态：待审核 */
    private static final int STATUS_PENDING = 0;
    /** 审核状态：已通过 */
    private static final int STATUS_APPROVED = 1;
    /** 审核状态：已拒绝 */
    private static final int STATUS_REJECTED = 2;

    /** 已支付 / 已完成 的订单状态 */
    private static final List<Integer> PAID_ORDER_STATUS = List.of(1, 5);

    private final MovieReviewMapper reviewMapper;
    private final MovieMapper movieMapper;
    private final OrderMapper orderMapper;
    private final SysUserMapper sysUserMapper;
    private final MovieCacheEvictHelper cacheEvictHelper;

    // ==================== 用户端 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovieReview create(Long userId, ReviewCreateDTO dto) {
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null || !Integer.valueOf(1).equals(movie.getStatus())) {
            throw new MovieFrameException("电影不存在或已下架");
        }

        Long paidOrderCount = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getMovieId, dto.getMovieId())
                .in(Order::getStatus, PAID_ORDER_STATUS));
        if (paidOrderCount == null || paidOrderCount == 0) {
            throw new MovieFrameException("购买并支付该电影后才可以评价");
        }

        Long existCount = reviewMapper.selectCount(new LambdaQueryWrapper<MovieReview>()
                .eq(MovieReview::getUserId, userId)
                .eq(MovieReview::getMovieId, dto.getMovieId()));
        if (existCount != null && existCount > 0) {
            throw new MovieFrameException("您已经评价过该电影，不能重复评价");
        }

        Date now = new Date();
        MovieReview review = new MovieReview();
        review.setMovieId(dto.getMovieId());
        review.setUserId(userId);
        review.setOrderId(dto.getOrderId());
        review.setRating(dto.getRating());
        review.setContent(StrUtil.trimToEmpty(dto.getContent()));
        // 先发后审：发布即通过，管理端可事后拒绝/删除
        review.setStatus(STATUS_APPROVED);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        save(review);

        log.info("[电影评价] 新增评价 movieId={} userId={} rating={}", dto.getMovieId(), userId, dto.getRating());
        evictMovieCaches(dto.getMovieId());
        return review;
    }

    @Override
    public Page<ReviewVO> pageByMovie(Long movieId, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<MovieReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MovieReview::getMovieId, movieId)
                .eq(MovieReview::getStatus, STATUS_APPROVED)
                .orderByDesc(MovieReview::getCreatedAt);
        Page<MovieReview> entityPage = reviewMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return toVoPage(entityPage);
    }

    @Override
    public MovieRatingVO movieRating(Long movieId) {
        if (movieId == null) {
            return MovieRatingVO.empty();
        }
        QueryWrapper<MovieReview> wrapper = new QueryWrapper<>();
        wrapper.select("AVG(rating) AS avg_rating", "COUNT(*) AS cnt")
                .eq("movie_id", movieId)
                .eq("status", STATUS_APPROVED);
        List<Map<String, Object>> rows = reviewMapper.selectMaps(wrapper);
        return toRating(rows.isEmpty() ? null : rows.get(0));
    }

    @Override
    public Map<Long, MovieRatingVO> batchRatings(Collection<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<MovieReview> wrapper = new QueryWrapper<>();
        wrapper.select("movie_id", "AVG(rating) AS avg_rating", "COUNT(*) AS cnt")
                .in("movie_id", movieIds)
                .eq("status", STATUS_APPROVED)
                .groupBy("movie_id");
        List<Map<String, Object>> rows = reviewMapper.selectMaps(wrapper);
        Map<Long, MovieRatingVO> result = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object movieIdValue = row.get("movie_id");
            if (movieIdValue == null) {
                continue;
            }
            result.put(Long.valueOf(movieIdValue.toString()), toRating(row));
        }
        return result;
    }

    @Override
    public Page<ReviewVO> myReviews(Long userId, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<MovieReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MovieReview::getUserId, userId)
                .orderByDesc(MovieReview::getCreatedAt);
        Page<MovieReview> entityPage = reviewMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return toVoPage(entityPage);
    }

    @Override
    public ReviewVO myReviewOfMovie(Long userId, Long movieId) {
        LambdaQueryWrapper<MovieReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MovieReview::getUserId, userId)
                .eq(MovieReview::getMovieId, movieId)
                .last("LIMIT 1");
        MovieReview review = reviewMapper.selectOne(wrapper);
        if (review == null) {
            return null;
        }
        return toVoList(List.of(review)).get(0);
    }

    // ==================== 管理端 ====================

    @Override
    public Page<ReviewVO> adminPage(ReviewPageQuery query) {
        LambdaQueryWrapper<MovieReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getMovieId() != null, MovieReview::getMovieId, query.getMovieId())
                .eq(query.getStatus() != null, MovieReview::getStatus, query.getStatus())
                .eq(query.getRating() != null, MovieReview::getRating, query.getRating())
                .like(StrUtil.isNotBlank(query.getKeyword()), MovieReview::getContent, query.getKeyword())
                .orderByDesc(MovieReview::getCreatedAt);
        Page<MovieReview> entityPage = reviewMapper.selectPage(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return toVoPage(entityPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        MovieReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new MovieFrameException("评价不存在");
        }
        if (!Objects.equals(review.getStatus(), status)) {
            review.setStatus(status);
            review.setUpdatedAt(new Date());
            reviewMapper.updateById(review);
            log.info("[电影评价] 审核状态变更 id={} status={}", id, status);
            evictMovieCaches(review.getMovieId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReview(Long id) {
        MovieReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new MovieFrameException("评价不存在");
        }
        reviewMapper.deleteById(id);
        log.info("[电影评价] 删除评价 id={} movieId={}", id, review.getMovieId());
        evictMovieCaches(review.getMovieId());
    }

    // ==================== 内部方法 ====================

    /**
     * 评价写操作后失效电影详情 + 电影列表缓存（评分内嵌在 VO 中）
     */
    private void evictMovieCaches(Long movieId) {
        try {
            cacheEvictHelper.evictMovieDetail(movieId);
            cacheEvictHelper.evictMovieListAll();
        } catch (Exception e) {
            log.warn("[电影评价] 缓存失效失败 movieId={}: {}", movieId, e.getMessage());
        }
    }

    /**
     * 聚合结果 Map 转 VO
     */
    private MovieRatingVO toRating(Map<String, Object> row) {
        if (row == null) {
            return MovieRatingVO.empty();
        }
        Object cnt = row.get("cnt");
        int count = cnt == null ? 0 : Integer.parseInt(cnt.toString());
        if (count == 0) {
            return MovieRatingVO.empty();
        }
        Object avg = row.get("avg_rating");
        BigDecimal avgRating = avg == null
                ? BigDecimal.ZERO
                : new BigDecimal(avg.toString()).setScale(1, RoundingMode.HALF_UP);
        return MovieRatingVO.builder().avgRating(avgRating).reviewCount(count).build();
    }

    /**
     * 实体分页转 VO 分页（批量补全电影名、用户昵称/头像，避免 N+1 查询）
     */
    private Page<ReviewVO> toVoPage(Page<MovieReview> entityPage) {
        Page<ReviewVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(toVoList(entityPage.getRecords()));
        return voPage;
    }

    private List<ReviewVO> toVoList(List<MovieReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> movieIds = reviews.stream().map(MovieReview::getMovieId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> userIds = reviews.stream().map(MovieReview::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> movieNameMap = movieIds.isEmpty()
                ? Collections.emptyMap()
                : movieMapper.selectBatchIds(movieIds).stream()
                        .collect(Collectors.toMap(Movie::getId, Movie::getName, (a, b) -> a));
        Map<Long, SysUser> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity(), (a, b) -> a));

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return reviews.stream().map(review -> {
            SysUser user = userMap.get(review.getUserId());
            return ReviewVO.builder()
                    .id(review.getId())
                    .movieId(review.getMovieId())
                    .movieName(movieNameMap.get(review.getMovieId()))
                    .userId(review.getUserId())
                    .nickname(user == null || StrUtil.isBlank(user.getNickname()) ? "匿名用户" : user.getNickname())
                    .avatar(user == null ? null : user.getAvatar())
                    .rating(review.getRating())
                    .content(review.getContent())
                    .status(review.getStatus())
                    .createdAt(review.getCreatedAt() == null ? null : formatter.format(review.getCreatedAt()))
                    .build();
        }).collect(Collectors.toList());
    }
}
