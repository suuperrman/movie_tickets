package org.javaup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.javaup.dto.Result;
import org.javaup.dto.cinema.CinemaMovieVO;
import org.javaup.dto.cinema.CinemaPageQuery;
import org.javaup.dto.cinema.CinemaVO;
import org.javaup.dto.movie.ScheduleVO;
import org.javaup.service.CinemaService;
import org.javaup.service.MovieService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端影院接口（/cinema）
 *
 * 接口清单：
 *   GET /cinema/page                影院分页（名称/城市/地址筛选）
 *   GET /cinema/cities              已开业城市列表
 *   GET /cinema/{id}                影院详情
 *   GET /cinema/{id}/movies         影院当日正在放映的电影
 *   GET /cinema/{id}/sessions       影院某电影某日期的场次
 */
@RestController
@RequestMapping("/cinema")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;
    private final MovieService movieService;

    @GetMapping("/page")
    public Result<Page<CinemaVO>> page(CinemaPageQuery query) {
        // 用户端只展示正常营业的影院
        query.setStatus(1);
        return Result.ok(cinemaService.pageQuery(query));
    }

    @GetMapping("/cities")
    public Result<List<String>> cities() {
        return Result.ok(cinemaService.cities());
    }

    @GetMapping("/{id}")
    public Result<CinemaVO> detail(@PathVariable Long id) {
        return Result.ok(cinemaService.detail(id));
    }

    @GetMapping("/{id}/movies")
    public Result<List<CinemaMovieVO>> movies(@PathVariable Long id,
                                              @RequestParam(required = false) String date) {
        return Result.ok(movieService.moviesForCinema(id, date));
    }

    @GetMapping("/{id}/sessions")
    public Result<List<ScheduleVO>> sessions(@PathVariable Long id,
                                             @RequestParam Long movieId,
                                             @RequestParam(required = false) String date) {
        return Result.ok(movieService.schedulesForMovie(movieId, id, date));
    }
}
