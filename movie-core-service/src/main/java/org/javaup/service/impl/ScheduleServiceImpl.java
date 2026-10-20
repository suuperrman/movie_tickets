package org.javaup.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.schedule.ScheduleAdminVO;
import org.javaup.dto.schedule.SchedulePageQuery;
import org.javaup.dto.schedule.ScheduleSaveDTO;
import org.javaup.entity.*;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.*;
import org.javaup.service.ScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 场次模块服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl extends ServiceImpl<ScheduleMapper, Schedule> implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final SeatMapper seatMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ==================== 1. 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Schedule create(ScheduleSaveDTO dto) {
        Movie movie = movieMapper.selectById(dto.getMovieId());
        if (movie == null) {
            throw new MovieFrameException("电影不存在");
        }
        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) {
            throw new MovieFrameException("影院不存在");
        }
        Hall hall = hallMapper.selectById(dto.getHallId());
        if (hall == null || !hall.getCinemaId().equals(dto.getCinemaId())) {
            throw new MovieFrameException("影厅不存在或不属于该影院");
        }

        Date start = parseDateTime(dto.getStartTime());
        Date end = parseDateTime(dto.getEndTime());
        if (start == null || end == null) {
            throw new MovieFrameException("时间格式错误");
        }
        if (!end.after(start)) {
            throw new MovieFrameException("散场时间必须晚于开场时间");
        }

        // 检查影厅同一时段是否已被占用
        Long conflict = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getHallId, dto.getHallId())
                .lt(Schedule::getStartTime, end)
                .gt(Schedule::getEndTime, start));
        if (conflict != null && conflict > 0) {
            throw new MovieFrameException("该影厅在此时段已有排片");
        }

        Schedule schedule = new Schedule();
        schedule.setMovieId(dto.getMovieId());
        schedule.setCinemaId(dto.getCinemaId());
        schedule.setHallId(dto.getHallId());
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setPrice(dto.getPrice());
        schedule.setTotalSeat(hall.getRowCount() * hall.getColCount());
        schedule.setAvailableSeat(schedule.getTotalSeat());
        schedule.setStatus(1);
        schedule.setVersion(0);
        schedule.setCreatedAt(new Date());
        schedule.setUpdatedAt(new Date());
        save(schedule);

        // 批量生成 schedule_seat 记录（初始全为可售）
        // 优先从 seat 表取物理座位；如果 seat 表为空则直接按 hall.rowCount × colCount 生成
        List<Seat> seats = seatMapper.selectList(
                new LambdaQueryWrapper<Seat>().eq(Seat::getHallId, dto.getHallId()).eq(Seat::getIsActive, 1));
        if (!seats.isEmpty()) {
            for (Seat seat : seats) {
                ScheduleSeat ss = new ScheduleSeat();
                ss.setScheduleId(schedule.getId());
                ss.setSeatId(seat.getId());
                ss.setRowNo(seat.getRowNo());
                ss.setColNo(seat.getColNo());
                ss.setPrice(dto.getPrice());
                ss.setStatus(0);
                ss.setLockUserId(0L);
                ss.setVersion(0);
                scheduleSeatMapper.insert(ss);
            }
        } else {
            // seat 表为空：直接根据 hall 尺寸生成，seat_id 用 rowNo*1000+colNo 保证唯一
            int rowCount = hall.getRowCount();
            int colCount = hall.getColCount();
            for (int r = 1; r <= rowCount; r++) {
                for (int c = 1; c <= colCount; c++) {
                    ScheduleSeat ss = new ScheduleSeat();
                    ss.setScheduleId(schedule.getId());
                    ss.setSeatId((long) r * 1000 + c);
                    ss.setRowNo(r);
                    ss.setColNo(c);
                    ss.setPrice(dto.getPrice());
                    ss.setStatus(0);
                    ss.setLockUserId(0L);
                    ss.setVersion(0);
                    scheduleSeatMapper.insert(ss);
                }
            }
        }

        log.info("新增场次 id={} movieId={} hallId={} start={}", schedule.getId(), dto.getMovieId(), dto.getHallId(), dto.getStartTime());
        return schedule;
    }

    // ==================== 2. 修改（仅允许改 price / 时间，改时间需重新检查冲突） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ScheduleSaveDTO dto) {
        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new MovieFrameException("场次不存在");
        }

        Date start = parseDateTime(dto.getStartTime());
        Date end = parseDateTime(dto.getEndTime());
        if (start != null && end != null && end.after(start)) {
            Long conflict = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getHallId, schedule.getHallId())
                    .lt(Schedule::getStartTime, end)
                    .gt(Schedule::getEndTime, start)
                    .ne(Schedule::getId, id));
            if (conflict != null && conflict > 0) {
                throw new MovieFrameException("该影厅在此时段已有排片");
            }
            schedule.setStartTime(start);
            schedule.setEndTime(end);
        }
        if (dto.getPrice() != null) {
            schedule.setPrice(dto.getPrice());
            // 同步更新 schedule_seat 的 price
            scheduleSeatMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ScheduleSeat>()
                    .eq(ScheduleSeat::getScheduleId, id)
                    .set(ScheduleSeat::getPrice, dto.getPrice()));
        }
        schedule.setUpdatedAt(new Date());
        updateById(schedule);
        log.info("修改场次 id={}", id);
    }

    // ==================== 3. 删除（有座位锁定/售票则拒绝） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new MovieFrameException("场次不存在");
        }

        Long locked = scheduleSeatMapper.selectCount(new LambdaQueryWrapper<ScheduleSeat>()
                .eq(ScheduleSeat::getScheduleId, id)
                .in(ScheduleSeat::getStatus, 1, 2)); // 1=锁定 2=已售
        if (locked != null && locked > 0) {
            throw new MovieFrameException("该场次已有 " + locked + " 个座位被锁定或售出，无法删除");
        }
        scheduleSeatMapper.delete(new LambdaQueryWrapper<ScheduleSeat>().eq(ScheduleSeat::getScheduleId, id));
        removeById(id);
        log.info("删除场次 id={}", id);
    }

    // ==================== 4. 启停用 ====================

    @Override
    public void updateStatus(Long id, Integer status) {
        Schedule schedule = getById(id);
        if (schedule == null) {
            throw new MovieFrameException("场次不存在");
        }
        schedule.setStatus(status);
        schedule.setUpdatedAt(new Date());
        updateById(schedule);
        log.info("场次启停用 id={} status={}", id, status);
    }

    // ==================== 5. 分页查询 ====================

    @Override
    public Page<ScheduleAdminVO> pageQuery(SchedulePageQuery query) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<>();
        if (query.getMovieId() != null) {
            wrapper.eq(Schedule::getMovieId, query.getMovieId());
        }
        if (query.getCinemaId() != null) {
            wrapper.eq(Schedule::getCinemaId, query.getCinemaId());
        }
        if (query.getHallId() != null) {
            wrapper.eq(Schedule::getHallId, query.getHallId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Schedule::getStatus, query.getStatus());
        }
        if (StrUtil.isNotBlank(query.getDate())) {
            LocalDate d = LocalDate.parse(query.getDate());
            Date dayStart = Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
            Date dayEnd = Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            wrapper.ge(Schedule::getStartTime, dayStart).lt(Schedule::getStartTime, dayEnd);
        }
        wrapper.orderByDesc(Schedule::getStartTime);

        Page<Schedule> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Page<ScheduleAdminVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        List<Long> movieIds = entityPage.getRecords().stream().map(Schedule::getMovieId).distinct().collect(Collectors.toList());
        List<Long> cinemaIds = entityPage.getRecords().stream().map(Schedule::getCinemaId).distinct().collect(Collectors.toList());
        List<Long> hallIds = entityPage.getRecords().stream().map(Schedule::getHallId).distinct().collect(Collectors.toList());
        Map<Long, String> movieNameMap = movieMapper.selectBatchIds(movieIds).stream().collect(Collectors.toMap(Movie::getId, Movie::getName));
        Map<Long, String> cinemaNameMap = cinemaMapper.selectBatchIds(cinemaIds).stream().collect(Collectors.toMap(Cinema::getId, Cinema::getName));
        Map<Long, String> hallNameMap = hallMapper.selectBatchIds(hallIds).stream().collect(Collectors.toMap(Hall::getId, Hall::getName));

        voPage.setRecords(entityPage.getRecords().stream().map(s -> ScheduleAdminVO.builder()
                .id(s.getId()).movieId(s.getMovieId()).movieName(movieNameMap.getOrDefault(s.getMovieId(), ""))
                .cinemaId(s.getCinemaId()).cinemaName(cinemaNameMap.getOrDefault(s.getCinemaId(), ""))
                .hallId(s.getHallId()).hallName(hallNameMap.getOrDefault(s.getHallId(), ""))
                .startTime(DT_FMT.format(LocalDateTime.ofInstant(s.getStartTime().toInstant(), ZoneId.systemDefault())))
                .endTime(DT_FMT.format(LocalDateTime.ofInstant(s.getEndTime().toInstant(), ZoneId.systemDefault())))
                .price(s.getPrice()).totalSeat(s.getTotalSeat()).availableSeat(s.getAvailableSeat())
                .status(s.getStatus()).version(s.getVersion())
                .createdAt(DT_FMT.format(LocalDateTime.ofInstant(s.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                .build()).collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 6. 详情 ====================

    @Override
    public ScheduleAdminVO detail(Long id) {
        Schedule s = getById(id);
        if (s == null) {
            throw new MovieFrameException("场次不存在");
        }
        String movieName = movieMapper.selectById(s.getMovieId()).getName();
        String cinemaName = cinemaMapper.selectById(s.getCinemaId()).getName();
        String hallName = hallMapper.selectById(s.getHallId()).getName();
        return ScheduleAdminVO.builder()
                .id(s.getId()).movieId(s.getMovieId()).movieName(movieName)
                .cinemaId(s.getCinemaId()).cinemaName(cinemaName)
                .hallId(s.getHallId()).hallName(hallName)
                .startTime(DT_FMT.format(LocalDateTime.ofInstant(s.getStartTime().toInstant(), ZoneId.systemDefault())))
                .endTime(DT_FMT.format(LocalDateTime.ofInstant(s.getEndTime().toInstant(), ZoneId.systemDefault())))
                .price(s.getPrice()).totalSeat(s.getTotalSeat()).availableSeat(s.getAvailableSeat())
                .status(s.getStatus()).version(s.getVersion())
                .createdAt(DT_FMT.format(LocalDateTime.ofInstant(s.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                .build();
    }

    // ==================== 内部辅助 ====================

    private static Date parseDateTime(String s) {
        try {
            return Date.from(LocalDateTime.parse(s, DT_FMT).atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            return null;
        }
    }
}
