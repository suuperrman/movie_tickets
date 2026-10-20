package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.hall.HallPageQuery;
import org.javaup.dto.hall.HallSaveDTO;
import org.javaup.dto.hall.HallVO;
import org.javaup.entity.Hall;

/**
 * 影厅模块服务接口
 */
public interface HallService extends IService<Hall> {

    Hall create(HallSaveDTO dto);
    void update(Long id, HallSaveDTO dto);
    void delete(Long id);
    Page<HallVO> pageQuery(HallPageQuery query);
    HallVO detail(Long id);
}
