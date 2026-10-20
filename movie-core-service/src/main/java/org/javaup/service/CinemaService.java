package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.cinema.CinemaPageQuery;
import org.javaup.dto.cinema.CinemaSaveDTO;
import org.javaup.dto.cinema.CinemaVO;
import org.javaup.entity.Cinema;

/**
 * 影院模块服务接口
 */
public interface CinemaService extends IService<Cinema> {

    Cinema create(CinemaSaveDTO dto);
    void update(Long id, CinemaSaveDTO dto);
    void delete(Long id);
    void updateStatus(Long id, Integer status);
    Page<CinemaVO> pageQuery(CinemaPageQuery query);
    CinemaVO detail(Long id);
}
