package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.schedule.ScheduleAdminVO;
import org.javaup.dto.schedule.SchedulePageQuery;
import org.javaup.dto.schedule.ScheduleSaveDTO;
import org.javaup.entity.Schedule;

/**
 * 场次模块服务接口
 */
public interface ScheduleService extends IService<Schedule> {

    Schedule create(ScheduleSaveDTO dto);
    void update(Long id, ScheduleSaveDTO dto);
    void delete(Long id);
    void updateStatus(Long id, Integer status);
    Page<ScheduleAdminVO> pageQuery(SchedulePageQuery query);
    ScheduleAdminVO detail(Long id);
}
