package org.javaup.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.core.RedisKeyManage;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.MultiLevelCacheService;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 电影模块缓存失效工具
 *
 * 职责：
 *   - 精准删除单 key（详情、分类列表）
 *   - SCAN 按前缀批量删除（列表、搜索、影院/场次）
 *
 * 说明：使用 SCAN 而非 KEYS，避免阻塞 Redis 主线程（参考 SeatLockAutoUnlockTask）。
 * RedisKeyBuild 会在 key 模板前拼接 prefix（项目名），因此 SCAN pattern 必须先取得完整前缀。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MovieCacheEvictHelper {

    private final StringRedisTemplate stringRedisTemplate;
    private final MultiLevelCacheService multiLevelCacheService;

    /**
     * 失效指定电影的详情缓存（用户端 + 管理端）
     */
    public void evictMovieDetail(Long movieId) {
        if (movieId == null) {
            return;
        }
        // 同时删除本地缓存
        multiLevelCacheService.evict(RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_DETAIL_USER_KEY, movieId));
        multiLevelCacheService.evict(RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_DETAIL_ADMIN_KEY, movieId));
    }

    /**
     * 失效所有电影详情缓存（用于分类/演员变更，影响所有详情中的关联字段）
     */
    public void evictAllMovieDetail() {
        scanAndDelete(buildPattern(RedisKeyManage.MOVIE_DETAIL_USER_KEY));
        scanAndDelete(buildPattern(RedisKeyManage.MOVIE_DETAIL_ADMIN_KEY));
    }

    /**
     * 失效所有电影列表/搜索缓存（热映、即将上映、搜索结果）
     */
    public void evictMovieListAll() {
        scanAndDelete(buildPattern(RedisKeyManage.MOVIE_NOW_SHOWING_KEY));
        scanAndDelete(buildPattern(RedisKeyManage.MOVIE_COMING_SOON_KEY));
        scanAndDelete(buildPattern(RedisKeyManage.MOVIE_SEARCH_KEY));
        // 同步删除本地缓存
        multiLevelCacheService.evictLocalByPrefix(RedisKeyBuild.getRedisKey(RedisKeyManage.MOVIE_NOW_SHOWING_KEY).replace("%s", ""));
        multiLevelCacheService.evictLocalByPrefix(RedisKeyBuild.getRedisKey(RedisKeyManage.MOVIE_COMING_SOON_KEY).replace("%s", ""));
        multiLevelCacheService.evictLocalByPrefix(RedisKeyBuild.getRedisKey(RedisKeyManage.MOVIE_SEARCH_KEY).replace("%s", ""));
    }

    /**
     * 失效指定电影的影院/场次列表缓存
     *
     * 调用方：电影写操作（覆盖更新）、场次写操作
     */
    public void evictMovieCinemas(Long movieId) {
        if (movieId == null) {
            return;
        }
        // movie:cinemas:{movieId}:*:*
        scanAndDelete(buildPattern(RedisKeyManage.MOVIE_CINEMAS_KEY, String.valueOf(movieId)));
        // movie:sessions:{movieId}:*:*
        scanAndDelete(buildPattern(RedisKeyManage.MOVIE_SESSIONS_KEY, String.valueOf(movieId)));
        // 同步删除本地缓存
        multiLevelCacheService.evictLocalByPrefix(RedisKeyBuild.getRedisKey(RedisKeyManage.MOVIE_CINEMAS_KEY).replace("%s", String.valueOf(movieId)));
        multiLevelCacheService.evictLocalByPrefix(RedisKeyBuild.getRedisKey(RedisKeyManage.MOVIE_SESSIONS_KEY).replace("%s", String.valueOf(movieId)));
    }

    /**
     * 失效指定场次的座位图缓存
     */
    public void evictSeatMap(Long scheduleId) {
        if (scheduleId == null) {
            return;
        }
        multiLevelCacheService.evict(RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_MAP_KEY, scheduleId));
    }

    /**
     * 失效电影分类列表缓存
     */
    public void evictCategoryList() {
        multiLevelCacheService.evict(RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_CATEGORY_LIST_KEY));
    }

    // ==================== 内部工具 ====================

    /**
     * 根据 RedisKeyManage 模板生成 SCAN pattern
     *
     * 思路：先用 RedisKeyBuild.getRedisKey 拿到带项目前缀的完整模板（含 %s），
     *       再把所有 %s 替换为 * 得到通配 pattern。
     *
     * 示例：MOVIE_NOW_SHOWING_KEY → "prefix-movie:list:now:*:*"
     *
     * @param extraLeadingArgs 已确定的前缀参数（例如 movieId），先按位置 format 进去，剩下的 %s 替换为 *
     */
    private String buildPattern(RedisKeyManage manage, String... extraLeadingArgs) {
        String template = RedisKeyBuild.getRedisKey(manage);
        // 先把已确定的前缀参数 format 进去
        if (extraLeadingArgs != null && extraLeadingArgs.length > 0) {
            // String.format 要求参数个数与 %s 个数一致，这里仅替换前 N 个
            // 简单做法：手动逐个替换 %s
            StringBuilder sb = new StringBuilder(template);
            for (String arg : extraLeadingArgs) {
                int idx = sb.indexOf("%s");
                if (idx < 0) {
                    break;
                }
                sb.replace(idx, idx + 2, arg);
            }
            template = sb.toString();
        }
        // 剩下的 %s 全部替换为 *
        return template.replace("%s", "*");
    }

    /**
     * 用 SCAN 安全遍历所有匹配 pattern 的 key，并批量删除
     */
    private void scanAndDelete(String pattern) {
        try {
            Set<String> keys = new HashSet<>();
            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                org.springframework.data.redis.core.Cursor<byte[]> cursor =
                        connection.scan(ScanOptions.scanOptions().match(pattern).count(50).build());
                try {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next()));
                    }
                } finally {
                    cursor.close();
                }
                return null;
            });
            if (!keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("[缓存失效] pattern={} 删除 {} 个 key", pattern, keys.size());
            } else {
                log.debug("[缓存失效] pattern={} 无匹配 key", pattern);
            }
        } catch (Exception e) {
            // 缓存失效失败不影响主流程，仅记日志
            log.warn("[缓存失效] pattern={} 异常: {}", pattern, e.getMessage());
        }
    }

    /**
     * 安静删除单 key（异常吞掉）
     */
    private void deleteQuietly(String key) {
        try {
            stringRedisTemplate.delete(key);
            log.debug("[缓存失效] key={}", key);
        } catch (Exception e) {
            log.warn("[缓存失效] key={} 异常: {}", key, e.getMessage());
        }
    }
}
