package org.javaup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.javaup.entity.Movie;
import org.javaup.entity.Schedule;
import org.javaup.handler.BloomFilterHandler;
import org.javaup.mapper.MovieMapper;
import org.javaup.mapper.ScheduleMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 布隆过滤器服务：防止缓存穿透。
 * <p>
 * 启动时将所有合法电影ID、场次ID加载到布隆过滤器中，
 * 业务查询前先校验ID是否存在于布隆过滤器，不存在则直接返回，
 * 避免大量不存在的ID穿透到数据库。
 * </p>
 * <p>
 * 注意：布隆过滤器存在误判率（可能把不存在的ID判断为存在），
 * 但不会漏判（存在的ID一定判断为存在），所以不会影响正常业务。
 * </p>
 * <p>
 * 注意：这里不能用 Lombok 的 @RequiredArgsConstructor + 字段 @Qualifier，
 * 因为 Lombok 不会把字段上的 @Qualifier 复制到构造参数上，
 * 会导致容器中两个 BloomFilterHandler（movie/schedule）按类型注入冲突。
 * 必须使用显式构造函数并把 @Qualifier 标在参数上。
 * </p>
 */
@Slf4j
@Service
public class BloomFilterService {

    private final BloomFilterHandler movieBloomFilter;

    private final BloomFilterHandler scheduleBloomFilter;

    private final MovieMapper movieMapper;

    private final ScheduleMapper scheduleMapper;

    public BloomFilterService(@Qualifier("movie") BloomFilterHandler movieBloomFilter,
                              @Qualifier("schedule") BloomFilterHandler scheduleBloomFilter,
                              MovieMapper movieMapper,
                              ScheduleMapper scheduleMapper) {
        this.movieBloomFilter = movieBloomFilter;
        this.scheduleBloomFilter = scheduleBloomFilter;
        this.movieMapper = movieMapper;
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * 启动时初始化布隆过滤器，加载所有合法ID
     */
    @PostConstruct
    public void init() {
        log.info("[布隆过滤器] 开始初始化...");
        long movieStart = System.currentTimeMillis();
        List<Movie> movies = movieMapper.selectList(
                new LambdaQueryWrapper<Movie>().select(Movie::getId));
        movies.forEach(m -> movieBloomFilter.add(String.valueOf(m.getId())));
        log.info("[布隆过滤器] 电影ID加载完成，数量={}，耗时={}ms",
                movies.size(), System.currentTimeMillis() - movieStart);

        long scheduleStart = System.currentTimeMillis();
        List<Schedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>().select(Schedule::getId));
        schedules.forEach(s -> scheduleBloomFilter.add(String.valueOf(s.getId())));
        log.info("[布隆过滤器] 场次ID加载完成，数量={}，耗时={}ms",
                schedules.size(), System.currentTimeMillis() - scheduleStart);
    }

    /**
     * 判断电影ID是否可能存在（布隆过滤器校验）
     *
     * @param movieId 电影ID
     * @return true=可能存在（需查DB确认），false=一定不存在
     */
    public boolean containsMovieId(Long movieId) {
        if (movieId == null) {
            return false;
        }
        return movieBloomFilter.contains(String.valueOf(movieId));
    }

    /**
     * 判断场次ID是否可能存在（布隆过滤器校验）
     *
     * @param scheduleId 场次ID
     * @return true=可能存在（需查DB确认），false=一定不存在
     */
    public boolean containsScheduleId(Long scheduleId) {
        if (scheduleId == null) {
            return false;
        }
        return scheduleBloomFilter.contains(String.valueOf(scheduleId));
    }

    /**
     * 新增电影时同步加入布隆过滤器
     */
    public void addMovieId(Long movieId) {
        if (movieId != null) {
            movieBloomFilter.add(String.valueOf(movieId));
        }
    }

    /**
     * 新增场次时同步加入布隆过滤器
     */
    public void addScheduleId(Long scheduleId) {
        if (scheduleId != null) {
            scheduleBloomFilter.add(String.valueOf(scheduleId));
        }
    }
}
