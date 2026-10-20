package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.seat.SeatPageQuery;
import org.javaup.dto.seat.SeatSaveDTO;
import org.javaup.dto.seat.SeatVO;
import org.javaup.entity.Seat;

/**
 * 物理座位模块服务接口
 */
public interface SeatService extends IService<Seat> {

    Seat create(SeatSaveDTO dto);
    void update(Long id, SeatSaveDTO dto);
    void delete(Long id);
    /** 批量生成影厅全部座位（hall.rowCount × hall.colCount） */
    int batchGenerate(Long hallId);
    Page<SeatVO> pageQuery(SeatPageQuery query);
    SeatVO detail(Long id);
}
