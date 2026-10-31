package org.javaup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.entity.MovieCategory;
import org.javaup.mapper.MovieCategoryMapper;
import org.javaup.redis.RedisCache;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.MovieCategoryService;
import org.javaup.utils.MovieCacheEvictHelper;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 电影分类服务实现
 *
 * 用户端 list 走 Redis 缓存（TTL 1h），管理端写操作完成后主动失效缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieCategoryServiceImpl extends ServiceImpl<MovieCategoryMapper, MovieCategory>
        implements MovieCategoryService {

    private final RedisCache redisCache;
    private final MovieCacheEvictHelper cacheEvictHelper;

    @Override
    public List<MovieCategory> listForUser() {
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_CATEGORY_LIST_KEY);
        try {
            List<MovieCategory> cached = redisCache.getValueIsList(key, MovieCategory.class);
            if (cached != null && !cached.isEmpty()) {
                log.debug("[缓存命中] categoryList");
                return cached;
            }
        } catch (Exception e) {
            log.warn("[缓存读取失败] categoryList: {}", e.getMessage());
        }

        List<MovieCategory> list = list(new LambdaQueryWrapper<MovieCategory>()
                .eq(MovieCategory::getStatus, 1)
                .orderByAsc(MovieCategory::getSort));

        try {
            redisCache.set(key, list, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[缓存写入失败] categoryList: {}", e.getMessage());
        }
        return list;
    }

    @Override
    public List<MovieCategory> listAll() {
        return list(new LambdaQueryWrapper<MovieCategory>().orderByAsc(MovieCategory::getSort));
    }

    @Override
    public MovieCategory createCategory(MovieCategory category) {
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        if (category.getSort() == null) {
            category.setSort(0);
        }
        category.setCreatedAt(new Date());
        category.setUpdatedAt(new Date());
        save(category);
        log.info("[Admin] 新增分类 id={} name={}", category.getId(), category.getName());
        // 分类变化会影响所有电影的 categories 字段，需失效分类列表 + 电影详情 + 搜索结果
        cacheEvictHelper.evictCategoryList();
        cacheEvictHelper.evictAllMovieDetail();
        cacheEvictHelper.evictMovieListAll();
        return category;
    }

    @Override
    public void updateCategory(Long id, MovieCategory category) {
        category.setId(id);
        category.setUpdatedAt(new Date());
        updateById(category);
        log.info("[Admin] 修改分类 id={}", id);
        // 分类名变化会影响所有电影的 categories 字段
        cacheEvictHelper.evictCategoryList();
        cacheEvictHelper.evictAllMovieDetail();
        cacheEvictHelper.evictMovieListAll();
    }

    @Override
    public void deleteCategory(Long id) {
        removeById(id);
        log.info("[Admin] 删除分类 id={}", id);
        // 删除分类会影响搜索按分类筛选的结果，以及详情中的 categories 字段
        cacheEvictHelper.evictCategoryList();
        cacheEvictHelper.evictAllMovieDetail();
        cacheEvictHelper.evictMovieListAll();
    }
}
