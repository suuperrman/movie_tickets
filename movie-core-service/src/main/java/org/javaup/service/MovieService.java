package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.movie.*;
import org.javaup.entity.Movie;

import java.util.List;

/**
 * 电影模块服务接口
 *
 * 所有方法对应 README.md 中的 12 个接口
 */
public interface MovieService extends IService<Movie> {

    // ========== 管理端 ==========
    Movie saveMovie(MovieSaveDTO dto);
    void updateMovie(Long id, MovieSaveDTO dto);
    void deleteMovie(Long id);
    void updateStatus(Long id, Integer status);
    Page<MovieListItemVO> pageQuery(MoviePageQuery query);
    MovieDetailVO detailForAdmin(Long id);

    // ========== 用户端 ==========
    Page<MovieListItemVO> nowShowing(MovieListQuery query);
    Page<MovieListItemVO> comingSoon(MovieListQuery query);
    MovieDetailVO detailForUser(Long id);
    Page<MovieListItemVO> search(MovieSearchQuery query);
    List<CinemaScheduleVO> cinemasForMovie(Long movieId, String city, String date);
    List<ScheduleVO> schedulesForMovie(Long movieId, Long cinemaId, String date);

    /**
     * 根据场次 ID 查座位图（选座页面使用）
     *
     * @param scheduleId 场次 ID
     * @return 座位图（含影厅布局 + 所有座位状态）
     */
    SeatMapVO seatMap(Long scheduleId);
}
