package org.javaup.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.movie.*;
import org.javaup.dto.review.MovieRatingVO;
import org.javaup.entity.*;
import org.javaup.enums.BaseCode;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.*;
import org.javaup.core.RedisKeyManage;
import org.javaup.redis.RedisCache;
import org.javaup.redis.RedisKeyBuild;
import org.javaup.service.BloomFilterService;
import org.javaup.service.MovieReviewService;
import org.javaup.service.MovieService;
import org.javaup.service.MultiLevelCacheService;
import org.javaup.utils.MovieCacheEvictHelper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 电影服务实现（12 个接口全部实现）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieServiceImpl extends ServiceImpl<MovieMapper, Movie> implements MovieService {

    private final MovieMapper movieMapper;
    private final MovieCategoryMapper categoryMapper;
    private final MovieCategoryRelMapper categoryRelMapper;
    private final ActorMapper actorMapper;
    private final MovieActorRelMapper actorRelMapper;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final RedisCache redisCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final MovieCacheEvictHelper cacheEvictHelper;
    private final BloomFilterService bloomFilterService;
    private final MultiLevelCacheService multiLevelCacheService;
    private final MovieReviewService movieReviewService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ========== 管理端：1. 新增 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Movie saveMovie(MovieSaveDTO dto) {
        // 1. 主表
        Movie movie = new Movie();
        movie.setName(dto.getName());
        movie.setDirector(dto.getDirector());
        movie.setDuration(dto.getDuration());
        movie.setReleaseDate(parseDate(dto.getReleaseDate()));
        movie.setPosterUrl(dto.getPosterUrl());
        movie.setDescription(dto.getDescription());
        movie.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        movie.setCreatedAt(new Date());
        movie.setUpdatedAt(new Date());
        save(movie);

        // 2. 分类关联
        saveCategoryRelations(movie.getId(), dto.getCategoryIds());

        // 3. 演员关联
        saveActorRelations(movie.getId(), dto.getActors());

        log.info("新增电影 id={} name={}", movie.getId(), movie.getName());
        // 同步加入布隆过滤器，防止新电影ID被误判为不存在
        bloomFilterService.addMovieId(movie.getId());
        // 失效列表/搜索缓存（新电影可能进入热映/即将上映/搜索结果）
        cacheEvictHelper.evictMovieListAll();
        return movie;
    }

    // ========== 管理端：2. 修改 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMovie(Long id, MovieSaveDTO dto) {
        Movie movie = getById(id);
        if (movie == null) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST); // TODO: 专用错误码
        }
        if (StrUtil.isNotBlank(dto.getName())) {
            movie.setName(dto.getName());
        }
        if (StrUtil.isNotBlank(dto.getDirector())) {
            movie.setDirector(dto.getDirector());
        }
        if (dto.getDuration() != null) {
            movie.setDuration(dto.getDuration());
        }
        if (StrUtil.isNotBlank(dto.getReleaseDate())) {
            movie.setReleaseDate(parseDate(dto.getReleaseDate()));
        }
        if (StrUtil.isNotBlank(dto.getPosterUrl())) {
            movie.setPosterUrl(dto.getPosterUrl());
        }
        if (dto.getDescription() != null) {
            movie.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            movie.setStatus(dto.getStatus());
        }
        movie.setUpdatedAt(new Date());
        updateById(movie);

        // 分类覆盖更新
        if (dto.getCategoryIds() != null) {
            categoryRelMapper.delete(new LambdaQueryWrapper<MovieCategoryRel>().eq(MovieCategoryRel::getMovieId, id));
            saveCategoryRelations(id, dto.getCategoryIds());
        }

        // 演员覆盖更新
        if (dto.getActors() != null) {
            actorRelMapper.delete(new LambdaQueryWrapper<MovieActorRel>().eq(MovieActorRel::getMovieId, id));
            saveActorRelations(id, dto.getActors());
        }

        log.info("修改电影 id={}", id);
        // 失效详情 / 列表 / 搜索 / 影院场次 缓存（改名字/分类/演员会影响搜索和详情）
        cacheEvictHelper.evictMovieDetail(id);
        cacheEvictHelper.evictMovieListAll();
        cacheEvictHelper.evictMovieCinemas(id);
    }

    // ========== 管理端：3. 删除（有进行中场次则拒绝） ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMovie(Long id) {
        Movie movie = getById(id);
        if (movie == null) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }

        // 检查是否有进行中场次（status=1 且 end_time > NOW()）
        Long activeCount = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMovieId, id)
                .eq(Schedule::getStatus, 1)
                .gt(Schedule::getEndTime, new Date()));
        if (activeCount != null && activeCount > 0) {
            throw new MovieFrameException("该电影存在进行中的场次，无法删除");
        }

        // 级联清理
        categoryRelMapper.delete(new LambdaQueryWrapper<MovieCategoryRel>().eq(MovieCategoryRel::getMovieId, id));
        actorRelMapper.delete(new LambdaQueryWrapper<MovieActorRel>().eq(MovieActorRel::getMovieId, id));
        scheduleMapper.delete(new LambdaQueryWrapper<Schedule>().eq(Schedule::getMovieId, id));
        removeById(id);

        log.info("删除电影 id={}", id);
        // 失效详情 / 列表 / 搜索 / 影院场次 缓存
        cacheEvictHelper.evictMovieDetail(id);
        cacheEvictHelper.evictMovieListAll();
        cacheEvictHelper.evictMovieCinemas(id);
    }

    // ========== 管理端：4. 上下架 ==========

    @Override
    public void updateStatus(Long id, Integer status) {
        Movie movie = getById(id);
        if (movie == null) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }
        movie.setStatus(status);
        movie.setUpdatedAt(new Date());
        updateById(movie);
        log.info("电影上下架 id={} status={}", id, status);
        // 上下架影响详情可见性 + 列表显示
        cacheEvictHelper.evictMovieDetail(id);
        cacheEvictHelper.evictMovieListAll();
    }

    // ========== 管理端：5. 分页查询 ==========

    @Override
    public Page<MovieListItemVO> pageQuery(MoviePageQuery query) {
        LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(Movie::getName, query.getKeyword())
                    .or().like(Movie::getDirector, query.getKeyword()));
        }
        if (query.getStatus() != null) {
            wrapper.eq(Movie::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Movie::getCreatedAt);

        Page<Movie> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return convertEntityPage(entityPage, false);
    }

    // ========== 管理端：6. 详情 ==========

    @Override
    public MovieDetailVO detailForAdmin(Long id) {
        // 布隆过滤器防穿透：ID不存在直接返回，不查DB不查缓存
        if (!bloomFilterService.containsMovieId(id)) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_DETAIL_ADMIN_KEY, id);
        Supplier<MovieDetailVO> dbFallback = () -> {
            Movie movie = getById(id);
            if (movie == null) {
                throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
            }
            return buildDetail(movie, true);
        };
        try {
            return redisCache.getWithMutex(key, MovieDetailVO.class, dbFallback, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[缓存互斥锁查询失败，降级直查DB] movieDetailAdmin id={}: {}", id, e.getMessage());
            return dbFallback.get();
        }
    }

    // ========== 用户端：7. 正在热映 ==========

    @Override
    public Page<MovieListItemVO> nowShowing(MovieListQuery query) {
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_NOW_SHOWING_KEY,
                query.getPageNo(), query.getPageSize());
        Supplier<Page<MovieListItemVO>> dbFallback = () -> {
            LocalDate today = LocalDate.now();
            LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Movie::getStatus, 1);
            wrapper.le(Movie::getReleaseDate, Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            wrapper.orderByDesc(Movie::getReleaseDate);
            return buildUserPage(wrapper, query.getPageNo(), query.getPageSize(), query.getCity());
        };
        try {
            // 多级缓存：Caffeine本地缓存 → Redis → DB
            return multiLevelCacheService.get(key,
                    new com.alibaba.fastjson.TypeReference<Page<MovieListItemVO>>(){}.getType(),
                    dbFallback, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[多级缓存查询失败，降级直查DB] nowShowing: {}", e.getMessage());
            return dbFallback.get();
        }
    }

    // ========== 用户端：8. 即将上映 ==========

    @Override
    public Page<MovieListItemVO> comingSoon(MovieListQuery query) {
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_COMING_SOON_KEY,
                query.getPageNo(), query.getPageSize());
        Supplier<Page<MovieListItemVO>> dbFallback = () -> {
            LocalDate today = LocalDate.now();
            LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Movie::getStatus, 1);
            wrapper.gt(Movie::getReleaseDate, Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            wrapper.orderByAsc(Movie::getReleaseDate);
            return buildUserPage(wrapper, query.getPageNo(), query.getPageSize(), query.getCity());
        };
        try {
            // 多级缓存：Caffeine本地缓存 → Redis → DB
            return multiLevelCacheService.get(key,
                    new com.alibaba.fastjson.TypeReference<Page<MovieListItemVO>>(){}.getType(),
                    dbFallback, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[多级缓存查询失败，降级直查DB] comingSoon: {}", e.getMessage());
            return dbFallback.get();
        }
    }

    // ========== 用户端：9. 详情 ==========

    @Override
    public MovieDetailVO detailForUser(Long id) {
        // 布隆过滤器防穿透：ID不存在直接返回
        if (!bloomFilterService.containsMovieId(id)) {
            throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
        }
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_DETAIL_USER_KEY, id);
        Supplier<MovieDetailVO> dbFallback = () -> {
            Movie movie = getById(id);
            if (movie == null || movie.getStatus() != 1) {
                throw new MovieFrameException(BaseCode.USER_NOT_EXIST);
            }
            return buildDetail(movie, false);
        };
        try {
            // 多级缓存：Caffeine本地缓存 → Redis → DB
            return multiLevelCacheService.get(key, MovieDetailVO.class, dbFallback, 30, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[多级缓存查询失败，降级直查DB] movieDetailUser id={}: {}", id, e.getMessage());
            return dbFallback.get();
        }
    }

    // ========== 用户端：10. 搜索 ==========

    @Override
    public Page<MovieListItemVO> search(MovieSearchQuery query) {
        String kw = StrUtil.isBlank(query.getKeyword()) ? "-" : query.getKeyword();
        String catId = query.getCategoryId() == null ? "-" : String.valueOf(query.getCategoryId());
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_SEARCH_KEY,
                kw, catId, query.getPageNo(), query.getPageSize());
        Supplier<Page<MovieListItemVO>> dbFallback = () -> {
            LambdaQueryWrapper<Movie> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Movie::getStatus, 1);
            if (StrUtil.isNotBlank(query.getKeyword())) {
                List<Long> movieIds = collectMovieIdsBySearch(query.getKeyword());
                if (movieIds.isEmpty()) {
                    return new Page<>(query.getPageNo(), query.getPageSize());
                }
                wrapper.in(Movie::getId, movieIds);
            }
            if (query.getCategoryId() != null) {
                List<Long> movieIds = categoryRelMapper.selectList(
                        new LambdaQueryWrapper<MovieCategoryRel>().eq(MovieCategoryRel::getCategoryId, query.getCategoryId()))
                        .stream().map(MovieCategoryRel::getMovieId).collect(Collectors.toList());
                if (movieIds.isEmpty()) {
                    return new Page<>(query.getPageNo(), query.getPageSize());
                }
                wrapper.in(Movie::getId, movieIds);
            }
            wrapper.orderByDesc(Movie::getReleaseDate);
            return buildUserPage(wrapper, query.getPageNo(), query.getPageSize(), null);
        };
        try {
            // 多级缓存：Caffeine本地缓存 → Redis → DB
            return multiLevelCacheService.get(key,
                    new com.alibaba.fastjson.TypeReference<Page<MovieListItemVO>>(){}.getType(),
                    dbFallback, 3, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[多级缓存查询失败，降级直查DB] search: {}", e.getMessage());
            return dbFallback.get();
        }
    }

    // ========== 用户端：11. 购票入口：哪些影院有该电影场次 ==========

    @Override
    public List<CinemaScheduleVO> cinemasForMovie(Long movieId, String city, String dateStr) {
        // 布隆过滤器防穿透：电影ID不存在直接返回空列表
        if (!bloomFilterService.containsMovieId(movieId)) {
            return Collections.emptyList();
        }
        LocalDate date = parseLocalDateOrToday(dateStr);
        String cityKey = StrUtil.isBlank(city) ? "-" : city;
        String dateKey = date.format(DATE_FMT);
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_CINEMAS_KEY,
                movieId, cityKey, dateKey);
        try {
            List<CinemaScheduleVO> cached = redisCache.getByType(key,
                    new com.alibaba.fastjson.TypeReference<List<CinemaScheduleVO>>(){}.getType());
            if (cached != null) {
                log.debug("[缓存命中] cinemasForMovie movieId={} city={} date={}", movieId, cityKey, dateKey);
                return cached;
            }
        } catch (Exception e) {
            log.warn("[缓存读取失败] cinemasForMovie: {}", e.getMessage());
        }

        Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        // 查出该电影指定日期内所有场次（过滤已结束的）
        List<Schedule> sessions = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMovieId, movieId)
                .ge(Schedule::getStartTime, dayStart)
                .lt(Schedule::getStartTime, dayEnd)
                .eq(Schedule::getStatus, 1)
                .gt(Schedule::getEndTime, new Date()));

        if (sessions.isEmpty()) {
            try {
                redisCache.set(key, Collections.<CinemaScheduleVO>emptyList(), 1, java.util.concurrent.TimeUnit.MINUTES);
            } catch (Exception ignored) {}
            return Collections.emptyList();
        }

        // 按 cinemaId 分组聚合
        Map<Long, List<Schedule>> byCinema = sessions.stream()
                .collect(Collectors.groupingBy(Schedule::getCinemaId));

        // 批量查影院信息
        List<Long> cinemaIds = new ArrayList<>(byCinema.keySet());
        List<Cinema> cinemas = cinemaMapper.selectBatchIds(cinemaIds);
        Map<Long, Cinema> cinemaMap = cinemas.stream().collect(Collectors.toMap(Cinema::getId, c -> c));

        List<CinemaScheduleVO> result = byCinema.entrySet().stream()
                .filter(e -> {
                    if (city != null && !city.isEmpty()) {
                        Cinema c = cinemaMap.get(e.getKey());
                        return c != null && city.equalsIgnoreCase(c.getCity());
                    }
                    return true;
                })
                .map(e -> {
                    Cinema cinema = cinemaMap.get(e.getKey());
                    List<Schedule> list = e.getValue();
                    BigDecimal minPrice = list.stream().map(Schedule::getPrice)
                            .min(Comparator.naturalOrder()).orElse(null);
                    String firstTime = list.stream().map(Schedule::getStartTime)
                            .min(Comparator.naturalOrder())
                            .map(t -> DATE_TIME_FMT.format(LocalDateTime.ofInstant(t.toInstant(), ZoneId.systemDefault())))
                            .orElse(null);
                    return CinemaScheduleVO.builder()
                            .cinemaId(cinema.getId())
                            .cinemaName(cinema.getName())
                            .city(cinema.getCity())
                            .address(cinema.getAddress())
                            .phone(cinema.getPhone())
                            .minPrice(minPrice)
                            .firstScheduleTime(firstTime)
                            .scheduleCount(list.size())
                            .build();
                })
                .sorted(Comparator.comparing(CinemaScheduleVO::getFirstScheduleTime))
                .collect(Collectors.toList());

        try {
            redisCache.set(key, result, 1, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("[缓存写入失败] cinemasForMovie: {}", e.getMessage());
        }
        return result;
    }

    // ========== 用户端：12. 指定影院指定日期场次 ==========

    @Override
    public List<ScheduleVO> schedulesForMovie(Long movieId, Long cinemaId, String dateStr) {
        // 布隆过滤器防穿透：电影ID不存在直接返回空列表
        if (!bloomFilterService.containsMovieId(movieId)) {
            return Collections.emptyList();
        }
        LocalDate date = parseLocalDateOrToday(dateStr);
        String dateKey = date.format(DATE_FMT);
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.MOVIE_SESSIONS_KEY,
                movieId, cinemaId, dateKey);
        try {
            List<ScheduleVO> cached = redisCache.getByType(key,
                    new com.alibaba.fastjson.TypeReference<List<ScheduleVO>>(){}.getType());
            if (cached != null) {
                log.debug("[缓存命中] schedulesForMovie movieId={} cinemaId={} date={}", movieId, cinemaId, dateKey);
                return cached;
            }
        } catch (Exception e) {
            log.warn("[缓存读取失败] schedulesForMovie: {}", e.getMessage());
        }

        Date dayStart = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date dayEnd = Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<Schedule> sessions = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getMovieId, movieId)
                .eq(Schedule::getCinemaId, cinemaId)
                .ge(Schedule::getStartTime, dayStart)
                .lt(Schedule::getStartTime, dayEnd)
                .eq(Schedule::getStatus, 1)
                .gt(Schedule::getEndTime, new Date())
                .orderByAsc(Schedule::getStartTime));

        if (sessions.isEmpty()) {
            try {
                redisCache.set(key, Collections.<ScheduleVO>emptyList(), 30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) {}
            return Collections.emptyList();
        }

        // 批量查影厅
        List<Long> hallIds = sessions.stream().map(Schedule::getHallId).distinct().collect(Collectors.toList());
        Map<Long, Hall> hallMap = hallMapper.selectBatchIds(hallIds).stream()
                .collect(Collectors.toMap(Hall::getId, h -> h));

        List<ScheduleVO> result = sessions.stream().map(s -> {
            Hall hall = hallMap.get(s.getHallId());
            return ScheduleVO.builder()
                    .scheduleId(s.getId())
                    .hallId(s.getHallId())
                    .hallName(hall != null ? hall.getName() : "")
                    .hallType(hall != null ? hall.getType() : 1)
                    .startTime(DATE_TIME_FMT.format(LocalDateTime.ofInstant(s.getStartTime().toInstant(), ZoneId.systemDefault())))
                    .endTime(DATE_TIME_FMT.format(LocalDateTime.ofInstant(s.getEndTime().toInstant(), ZoneId.systemDefault())))
                    .price(s.getPrice())
                    .totalSeat(s.getTotalSeat())
                    .availableSeat(s.getAvailableSeat())
                    .status(s.getStatus())
                    .build();
        }).collect(Collectors.toList());

        try {
            redisCache.set(key, result, 30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[缓存写入失败] schedulesForMovie: {}", e.getMessage());
        }
        return result;
    }

    // ========== 用户端：13. 选座页面座位图 ==========

    @Override
    @Transactional(readOnly = true)
    public SeatMapVO seatMap(Long scheduleId) {
        // 布隆过滤器防穿透：场次ID不存在直接返回
        if (!bloomFilterService.containsScheduleId(scheduleId)) {
            throw new MovieFrameException("场次不存在或已停售");
        }
        // ★ 先校验场次有效性（不能只靠缓存！缓存命中也可能场次已过期）
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getStatus() != 1) {
            throw new MovieFrameException("场次不存在或已停售");
        }
        if (schedule.getEndTime() != null && schedule.getEndTime().before(new Date())) {
            // 过期场次 → 清掉残留缓存 + 报错
            try {
                String cacheKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_MAP_KEY, scheduleId).getRelKey();
                stringRedisTemplate.delete(cacheKey);
            } catch (Exception ignored) {}
            throw new MovieFrameException("场次已结束，无法查看座位");
        }

        // ★ Redis 缓存：场次有效才查缓存（互斥锁防击穿）
        RedisKeyBuild key = RedisKeyBuild.createRedisKey(RedisKeyManage.SCHEDULE_SEAT_MAP_KEY, scheduleId);
        Supplier<SeatMapVO> dbFallback = () -> doSeatMapQuery(scheduleId);
        try {
            return redisCache.getWithMutex(key, SeatMapVO.class, dbFallback, 100, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[缓存互斥锁查询失败，降级直查DB] seatmap scheduleId={}: {}", scheduleId, e.getMessage());
            return dbFallback.get();
        }
    }

    /**
     * 真实 DB 查询 + Bitmap 编码（被 seatMap 和其他需要绕开缓存的调用复用）
     */
    private SeatMapVO doSeatMapQuery(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || schedule.getStatus() != 1) {
            throw new MovieFrameException("场次不存在或已停售");
        }
        // ★ 过期场次也拒绝查询（防止用户手动改 URL 绕过）
        if (schedule.getEndTime() != null && schedule.getEndTime().before(new Date())) {
            throw new MovieFrameException("场次已结束，无法查看座位");
        }

        Hall hall = hallMapper.selectById(schedule.getHallId());
        if (hall == null) {
            throw new MovieFrameException("影厅不存在");
        }

        Cinema cinema = cinemaMapper.selectById(schedule.getCinemaId());
        Movie movie = movieMapper.selectById(schedule.getMovieId());

        int rowCount = hall.getRowCount();
        int colCount = hall.getColCount();
        int totalSeats = rowCount * colCount;

        // ★ SQL 投影查询：只 SELECT 四列（id/row_no/col_no/status），去掉 lock_user_id/order_id 等大字段
        List<ScheduleSeat> seats = scheduleSeatMapper.selectList(
                new LambdaQueryWrapper<ScheduleSeat>()
                        .select(ScheduleSeat::getId, ScheduleSeat::getRowNo,
                                ScheduleSeat::getColNo, ScheduleSeat::getStatus)
                        .eq(ScheduleSeat::getScheduleId, scheduleId)
        );

        // 构建扁平数组：index = rowNo*colCount + colNo（从 0 开始）
        String[] seatIds = new String[totalSeats];
        int[] statusArr = new int[totalSeats];

        if (!seats.isEmpty()) {
            for (ScheduleSeat s : seats) {
                int idx = (s.getRowNo() - 1) * colCount + (s.getColNo() - 1);
                seatIds[idx] = String.valueOf(s.getId());
                statusArr[idx] = s.getStatus() != null ? s.getStatus() : 0;
            }
            // 补全空洞（DB 缺座位）
            for (int i = 0; i < totalSeats; i++) {
                if (seatIds[i] == null) { seatIds[i] = String.valueOf(0L); }
            }
        } else {
            // 虚拟生成：全可售，ID 用虚拟雪花（r*100000 + c）
            for (int r = 1; r <= rowCount; r++) {
                for (int c = 1; c <= colCount; c++) {
                    int idx = (r - 1) * colCount + (c - 1);
                    seatIds[idx] = String.valueOf((long) r * 100000L + c);
                    statusArr[idx] = 0;
                }
            }
        }

        // ★ Bitmap 编码：2 bit / 座，大端序，Base64
        // 每个 byte 存 4 个座位：最高 2 bit → idx 0，最低 2 bit → idx 3
        int byteLen = (totalSeats + 3) / 4; // 向上取整
        byte[] bitmap = new byte[byteLen];
        for (int i = 0; i < totalSeats; i++) {
            // status 映射：0→00, 1→01, 2→10 （前端按 &0x03 解码）
            int s = statusArr[i];
            if (s > 2) {
                s = 0; // 兜底
            }
            int byteIdx = i / 4;
            int bitPos = (3 - (i % 4)) * 2; // 大端：idx 0 占最高 2 bit
            bitmap[byteIdx] |= (byte) ((s & 0x03) << bitPos);
        }
        String bitmapBase64 = java.util.Base64.getEncoder().encodeToString(bitmap);

        List<String> seatIdList = Arrays.asList(seatIds);

        return SeatMapVO.builder()
                .scheduleId(schedule.getId())
                .movieId(schedule.getMovieId())
                .movieName(movie != null ? movie.getName() : "")
                .posterUrl(movie != null ? movie.getPosterUrl() : null)
                .cinemaId(schedule.getCinemaId())
                .cinemaName(cinema != null ? cinema.getName() : "")
                .hallId(hall.getId())
                .hallName(hall.getName())
                .hallType(hall.getType())
                .startTime(DATE_TIME_FMT.format(LocalDateTime.ofInstant(schedule.getStartTime().toInstant(), ZoneId.systemDefault())))
                .endTime(DATE_TIME_FMT.format(LocalDateTime.ofInstant(schedule.getEndTime().toInstant(), ZoneId.systemDefault())))
                .price(schedule.getPrice())
                .rowCount(rowCount)
                .colCount(colCount)
                .seatIds(seatIdList)
                .seatStatusBitmap(bitmapBase64)
                .build();
    }

    // ==================== 内部工具方法 ====================

    /** 分类关联（先检查 category 是否存在） */
    private void saveCategoryRelations(Long movieId, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        List<MovieCategory> cats = categoryMapper.selectBatchIds(categoryIds);
        int sort = 0;
        for (MovieCategory cat : cats) {
            MovieCategoryRel rel = new MovieCategoryRel();
            rel.setMovieId(movieId);
            rel.setCategoryId(cat.getId());
            rel.setSort(sort++);
            rel.setCreatedAt(new Date());
            categoryRelMapper.insert(rel);
        }
    }


    /** 演员关联 */
    private void saveActorRelations(Long movieId, List<MovieSaveDTO.ActorItem> actorItems) {
        if (actorItems == null || actorItems.isEmpty()) {
            return;
        }
        for (int i = 0; i < actorItems.size(); i++) {
            MovieSaveDTO.ActorItem item = actorItems.get(i);
            Long actorId = item.getActorId();
            if (actorId == null) {
                // 新建演员
                Actor a = new Actor();
                a.setName(item.getName());
                a.setAvatarUrl(item.getAvatarUrl() != null ? item.getAvatarUrl() : "");
                a.setGender(item.getGender() != null ? item.getGender() : 0);
                a.setIntro(item.getIntro() != null ? item.getIntro() : "");
                a.setStatus(1);
                a.setCreatedAt(new Date());
                a.setUpdatedAt(new Date());
                actorMapper.insert(a);
                actorId = a.getId();
            }
            MovieActorRel rel = new MovieActorRel();
            rel.setMovieId(movieId);
            rel.setActorId(actorId);
            rel.setRoleName(item.getRoleName() != null ? item.getRoleName() : "");
            rel.setIsLead(item.getIsLead() != null ? item.getIsLead() : 0);
            rel.setSort(item.getSort() != null ? item.getSort() : i);
            rel.setCreatedAt(new Date());
            actorRelMapper.insert(rel);
        }
    }

    /** Movie 实体 → 列表 VO 分页 */
    private Page<MovieListItemVO> convertEntityPage(Page<Movie> entityPage, boolean forUser) {
        Page<MovieListItemVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        List<Long> movieIds = entityPage.getRecords().stream().map(Movie::getId).collect(Collectors.toList());
        Map<Long, String> categoryNamesMap = loadCategoryNames(movieIds);
        Map<Long, BigDecimal> minPriceMap = loadMinPrice(movieIds);
        Map<Long, Integer> cinemaCountMap = loadCinemaCount(movieIds);
        Map<Long, Integer> scheduleCountMap = loadScheduleCount(movieIds);
        Map<Long, MovieRatingVO> ratingMap = movieReviewService.batchRatings(movieIds);

        voPage.setRecords(entityPage.getRecords().stream().map(m -> {
            MovieRatingVO rating = ratingMap.getOrDefault(m.getId(), MovieRatingVO.empty());
            MovieListItemVO vo = MovieListItemVO.builder()
                    .id(m.getId()).name(m.getName()).director(m.getDirector())
                    .duration(m.getDuration()).posterUrl(m.getPosterUrl())
                    .status(m.getStatus()).description(m.getDescription())
                    .releaseDate(formatDate(m.getReleaseDate()))
                    .categories(categoryNamesMap.getOrDefault(m.getId(), ""))
                    .avgRating(rating.getAvgRating())
                    .reviewCount(rating.getReviewCount())
                    .build();
            if (forUser) {
                vo.setMinPrice(minPriceMap.get(m.getId()));
                vo.setCinemaCount(cinemaCountMap.getOrDefault(m.getId(), 0));
            } else {
                vo.setScheduleCount(scheduleCountMap.getOrDefault(m.getId(), 0));
            }
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 用户端列表分页（带 minPrice + cinemaCount，SQL 层面过滤 0 影院）
     *
     * 过滤逻辑：必须存在 schedule.status=1（可售）的排片。
     * 纯内存过滤 cinemaCount=0 会导致分页 total 不准，必须提前在 WHERE 里 IN 限制。
     */
    private Page<MovieListItemVO> buildUserPage(LambdaQueryWrapper<Movie> wrapper, Integer pageNo, Integer pageSize, String city) {
        // 先拿到所有有可售排片的 movieId
        QueryWrapper<Schedule> schQw = new QueryWrapper<>();
        schQw.select("DISTINCT movie_id").eq("status", 1);
        List<Map<String, Object>> rows = scheduleMapper.selectMaps(schQw);
        List<Long> onSaleMovieIds = rows.stream()
                .map(r -> ((Number) r.get("movie_id")).longValue())
                .collect(Collectors.toList());

        if (onSaleMovieIds.isEmpty()) {
            return new Page<>(pageNo, pageSize); // 没有任何可售排片，直接返回空页
        }

        // 关键：IN 限制让 MyBatis-Plus 的 page(count SQL) 自然只数可售电影，total 正确
        wrapper.in(Movie::getId, onSaleMovieIds);

        Page<Movie> entityPage = page(new Page<>(pageNo, pageSize), wrapper);
        Page<MovieListItemVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        List<Long> movieIds = entityPage.getRecords().stream().map(Movie::getId).collect(Collectors.toList());
        if (movieIds.isEmpty()) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        Map<Long, String> categoryNamesMap = loadCategoryNames(movieIds);
        Map<Long, BigDecimal> minPriceMap = loadMinPrice(movieIds);
        Map<Long, Integer> cinemaCountMap = loadCinemaCount(movieIds);
        Map<Long, MovieRatingVO> ratingMap = movieReviewService.batchRatings(movieIds);

        voPage.setRecords(entityPage.getRecords().stream().map(m -> {
            MovieRatingVO rating = ratingMap.getOrDefault(m.getId(), MovieRatingVO.empty());
            return MovieListItemVO.builder()
                .id(m.getId()).name(m.getName()).director(m.getDirector())
                .duration(m.getDuration()).posterUrl(m.getPosterUrl())
                .status(m.getStatus())
                .releaseDate(formatDate(m.getReleaseDate()))
                .categories(categoryNamesMap.getOrDefault(m.getId(), ""))
                .minPrice(minPriceMap.get(m.getId()))
                .cinemaCount(cinemaCountMap.getOrDefault(m.getId(), 0))
                .avgRating(rating.getAvgRating())
                .reviewCount(rating.getReviewCount())
                .build();
        }).collect(Collectors.toList()));
        return voPage;
    }

    /** 构建详情 VO */
    private MovieDetailVO buildDetail(Movie movie, boolean forAdmin) {
        // 分类
        List<MovieCategoryRel> rels = categoryRelMapper.selectList(
                new LambdaQueryWrapper<MovieCategoryRel>().eq(MovieCategoryRel::getMovieId, movie.getId())
                        .orderByAsc(MovieCategoryRel::getSort));
        List<MovieDetailVO.CategoryItem> categories = new ArrayList<>();
        if (!rels.isEmpty()) {
            List<Long> catIds = rels.stream().map(MovieCategoryRel::getCategoryId).collect(Collectors.toList());
            categories = categoryMapper.selectBatchIds(catIds).stream()
                    .map(c -> MovieDetailVO.CategoryItem.builder().id(c.getId()).name(c.getName()).build())
                    .collect(Collectors.toList());
        }

        // 演员
        List<MovieActorRel> actorRels = actorRelMapper.selectList(
                new LambdaQueryWrapper<MovieActorRel>().eq(MovieActorRel::getMovieId, movie.getId())
                        .orderByAsc(MovieActorRel::getIsLead).orderByAsc(MovieActorRel::getSort));
        List<MovieDetailVO.ActorItem> actors = new ArrayList<>();
        if (!actorRels.isEmpty()) {
            List<Long> actorIds = actorRels.stream().map(MovieActorRel::getActorId).collect(Collectors.toList());
            Map<Long, Actor> actorMap = actorMapper.selectBatchIds(actorIds).stream()
                    .collect(Collectors.toMap(Actor::getId, a -> a));
            actors = actorRels.stream().map(ar -> {
                Actor a = actorMap.get(ar.getActorId());
                return MovieDetailVO.ActorItem.builder()
                        .id(ar.getActorId())
                        .name(a != null ? a.getName() : "")
                        .avatarUrl(a != null ? a.getAvatarUrl() : "")
                        .roleName(ar.getRoleName())
                        .build();
            }).collect(Collectors.toList());
        }

        MovieRatingVO rating = movieReviewService.movieRating(movie.getId());
        MovieDetailVO.MovieDetailVOBuilder builder = MovieDetailVO.builder()
                .id(movie.getId()).name(movie.getName()).director(movie.getDirector())
                .duration(movie.getDuration()).releaseDate(formatDate(movie.getReleaseDate()))
                .posterUrl(movie.getPosterUrl()).description(movie.getDescription())
                .status(movie.getStatus())
                .categories(categories).actors(actors)
                .avgRating(rating.getAvgRating()).reviewCount(rating.getReviewCount());

        if (!forAdmin) {
            builder.minPrice(loadMinPriceSingle(movie.getId())).cinemaCount(loadCinemaCountSingle(movie.getId()));
        } else {
            // 管理端：附带近期 20 条场次
            List<Schedule> sessions = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getMovieId, movie.getId())
                    .orderByAsc(Schedule::getStartTime)
                    .last("LIMIT 20"));
            if (!sessions.isEmpty()) {
                List<Long> cinemaIds = sessions.stream().map(Schedule::getCinemaId).distinct().collect(Collectors.toList());
                Map<Long, Cinema> cinemaMap = cinemaMapper.selectBatchIds(cinemaIds).stream()
                        .collect(Collectors.toMap(Cinema::getId, c -> c));
                List<Long> hallIds = sessions.stream().map(Schedule::getHallId).distinct().collect(Collectors.toList());
                Map<Long, Hall> hallMap = hallMapper.selectBatchIds(hallIds).stream()
                        .collect(Collectors.toMap(Hall::getId, h -> h));
                builder.schedules(sessions.stream().map(s -> MovieDetailVO.ScheduleItem.builder()
                        .scheduleId(s.getId())
                        .cinemaName(cinemaMap.getOrDefault(s.getCinemaId(), new Cinema()).getName())
                        .hallName(hallMap.getOrDefault(s.getHallId(), new Hall()).getName())
                        .startTime(DATE_TIME_FMT.format(LocalDateTime.ofInstant(s.getStartTime().toInstant(), ZoneId.systemDefault())))
                        .endTime(DATE_TIME_FMT.format(LocalDateTime.ofInstant(s.getEndTime().toInstant(), ZoneId.systemDefault())))
                        .price(s.getPrice()).availableSeat(s.getAvailableSeat()).build())
                        .collect(Collectors.toList()));
            }
        }

        return builder.build();
    }

    // ==================== 批量查询辅助 ====================

    private Map<Long, String> loadCategoryNames(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MovieCategoryRel> rels = categoryRelMapper.selectList(
                new LambdaQueryWrapper<MovieCategoryRel>().in(MovieCategoryRel::getMovieId, movieIds));
        if (rels.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> catIds = rels.stream().map(MovieCategoryRel::getCategoryId).distinct().collect(Collectors.toList());
        Map<Long, String> catNameMap = categoryMapper.selectBatchIds(catIds).stream()
                .collect(Collectors.toMap(MovieCategory::getId, MovieCategory::getName));
        return rels.stream().collect(Collectors.groupingBy(
                MovieCategoryRel::getMovieId,
                Collectors.mapping(r -> catNameMap.getOrDefault(r.getCategoryId(), ""),
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> String.join("/", list)))));
    }

    private Map<Long, BigDecimal> loadMinPrice(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<Schedule> qw = new QueryWrapper<>();
        qw.select("movie_id", "MIN(price) AS min_price")
                .in("movie_id", movieIds)
                .eq("status", 1)
                .groupBy("movie_id");
        List<Map<String, Object>> rows = scheduleMapper.selectMaps(qw);
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("movie_id")).longValue(), (BigDecimal) row.get("min_price"));
        }
        return result;
    }

    private Map<Long, Integer> loadCinemaCount(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<Schedule> qw = new QueryWrapper<>();
        qw.select("movie_id", "COUNT(DISTINCT cinema_id) AS cinema_count")
                .in("movie_id", movieIds)
                .eq("status", 1)
                .groupBy("movie_id");
        List<Map<String, Object>> rows = scheduleMapper.selectMaps(qw);
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("movie_id")).longValue(), ((Number) row.get("cinema_count")).intValue());
        }
        return result;
    }

    private Integer loadCinemaCountSingle(Long movieId) {
        QueryWrapper<Schedule> qw = new QueryWrapper<>();
        qw.select("COUNT(DISTINCT cinema_id) AS cinema_count")
                .eq("movie_id", movieId).eq("status", 1);
        Map<String, Object> row = scheduleMapper.selectMaps(qw).stream().findFirst().orElse(null);
        return row != null ? ((Number) row.get("cinema_count")).intValue() : 0;
    }

    private BigDecimal loadMinPriceSingle(Long movieId) {
        QueryWrapper<Schedule> qw = new QueryWrapper<>();
        qw.select("MIN(price) AS min_price")
                .eq("movie_id", movieId).eq("status", 1);
        Map<String, Object> row = scheduleMapper.selectMaps(qw).stream().findFirst().orElse(null);
        return row != null ? (BigDecimal) row.get("min_price") : null;
    }

    private Map<Long, Integer> loadScheduleCount(List<Long> movieIds) {
        if (movieIds == null || movieIds.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<Schedule> qw = new QueryWrapper<>();
        qw.select("movie_id", "COUNT(*) AS cnt")
                .in("movie_id", movieIds)
                .groupBy("movie_id");
        List<Map<String, Object>> rows = scheduleMapper.selectMaps(qw);
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(((Number) row.get("movie_id")).longValue(), ((Number) row.get("cnt")).intValue());
        }
        return result;
    }

    /** 搜索：匹配 movie.name / director / actor.name 找到 movieIds */
    private List<Long> collectMovieIdsBySearch(String keyword) {
        Set<Long> result = new HashSet<>();
        // movie.name / director
        List<Movie> movies = list(new LambdaQueryWrapper<Movie>()
                .like(Movie::getName, keyword)
                .or().like(Movie::getDirector, keyword));
        movies.forEach(m -> result.add(m.getId()));

        // actor.name
        List<Actor> actors = actorMapper.selectList(new LambdaQueryWrapper<Actor>().like(Actor::getName, keyword));
        if (!actors.isEmpty()) {
            List<Long> actorIds = actors.stream().map(Actor::getId).collect(Collectors.toList());
            List<MovieActorRel> rels = actorRelMapper.selectList(
                    new LambdaQueryWrapper<MovieActorRel>().in(MovieActorRel::getActorId, actorIds));
            rels.forEach(r -> result.add(r.getMovieId()));
        }
        return new ArrayList<>(result);
    }

    // ==================== 工具 ====================

    private static SimpleDateFormat DATE_SDF = new SimpleDateFormat("yyyy-MM-dd");

    private static Date parseDate(String s) {
        try { return DATE_SDF.parse(s); } catch (Exception e) { return null; }
    }

    private static String formatDate(Date d) {
        if (d == null) {
            return null;
        }
        return DATE_FMT.format(LocalDate.ofInstant(d.toInstant(), ZoneId.systemDefault()));
    }

    private static LocalDate parseLocalDateOrToday(String s) {
        if (StrUtil.isBlank(s)) {
            return LocalDate.now();
        }
        return LocalDate.parse(s, DATE_FMT);
    }
}
