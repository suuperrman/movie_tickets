package org.javaup.mapper;

import org.apache.ibatis.annotations.Param;
import org.javaup.dto.movie.SeatMapRow;
import org.javaup.entity.Schedule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author xiao
* @description 针对表【schedule(场次表)】的数据库操作Mapper
* @createDate 2026-10-20 22:04:32
* @Entity org.javaup.entity.Schedule
*/
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 座位图元信息：场次 + 影厅 + 影院 + 影片，恒返回 1 行。
     * 座位明细由 ScheduleSeatMapper 按 schedule_id 单独查询（避免 JOIN 把元信息重复 N 份）。
     *
     * @param scheduleId 场次ID
     * @return 场次不存在时返回 null
     */
    SeatMapRow selectSeatMapMeta(@Param("scheduleId") Long scheduleId);
}




