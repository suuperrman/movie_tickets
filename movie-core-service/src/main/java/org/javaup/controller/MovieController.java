package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.Result;
import org.javaup.dto.movie.*;
import org.javaup.service.MovieService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端电影接口（/movie）
 */
@Slf4j
@RestController
@RequestMapping("/movie")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    // ==================== 正在热映 ====================

    @GetMapping("/now-showing")
    public Result<Page<MovieListItemVO>> nowShowing(MovieListQuery query) {
        log.info("[公开] GET /movie/now-showing pageNo={} pageSize={}", query.getPageNo(), query.getPageSize());
        return Result.ok(movieService.nowShowing(query));
    }

    // ==================== 即将上映 ====================

    @GetMapping("/coming-soon")
    public Result<Page<MovieListItemVO>> comingSoon(MovieListQuery query) {
        return Result.ok(movieService.comingSoon(query));
    }

    // ==================== 详情 ====================
    // {id:\d+} 只匹配纯数字，防止 /movie/now-showing 被误匹配成 id="now-showing"

    @GetMapping("/{id:\\d+}")
    public Result<MovieDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(movieService.detailForUser(id));
    }

    // ==================== 搜索 ====================

    @GetMapping("/search")
    public Result<Page<MovieListItemVO>> search(MovieSearchQuery query) {
        return Result.ok(movieService.search(query));
    }

    // ==================== 购票入口：哪些影院有该电影场次 ====================

    @GetMapping("/{id:\\d+}/cinemas")
    public Result<List<CinemaScheduleVO>> cinemas(
            @PathVariable("id") Long id,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "date", required = false) String date) {
        return Result.ok(movieService.cinemasForMovie(id, city, date));
    }

    // ==================== 指定影院指定日期场次 ====================

    @GetMapping("/{id:\\d+}/sessions")
    public Result<List<ScheduleVO>> schedules(
            @PathVariable("id") Long id,
            @RequestParam("cinemaId") Long cinemaId,
            @RequestParam(value = "date", required = false) String date) {
        return Result.ok(movieService.schedulesForMovie(id, cinemaId, date));
    }

    // ==================== 13. 选座：场次座位图 ====================

    @GetMapping("/schedule/{scheduleId:\\d+}/seats")
    public Result<SeatMapVO> seatMap(@PathVariable("scheduleId") Long scheduleId) {
        log.info("[公开] GET /movie/schedule/{}/seats", scheduleId);
        return Result.ok(movieService.seatMap(scheduleId));
    }
}
