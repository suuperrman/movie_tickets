package org.javaup.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.hall.HallPageQuery;
import org.javaup.dto.hall.HallSaveDTO;
import org.javaup.dto.hall.HallVO;
import org.javaup.entity.Cinema;
import org.javaup.entity.Hall;
import org.javaup.entity.Schedule;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.CinemaMapper;
import org.javaup.mapper.HallMapper;
import org.javaup.mapper.ScheduleMapper;
import org.javaup.service.HallService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 影厅模块服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HallServiceImpl extends ServiceImpl<HallMapper, Hall> implements HallService {

    private final HallMapper hallMapper;
    private final CinemaMapper cinemaMapper;
    private final ScheduleMapper scheduleMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 影厅类型描述映射 */
    private static final Map<Integer, String> HALL_TYPE_DESC = Map.of(
            1, "普通厅",
            2, "IMAX 厅",
            3, "情侣厅",
            4, "VIP 厅"
    );

    // ==================== 1. 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Hall create(HallSaveDTO dto) {
        Cinema cinema = cinemaMapper.selectById(dto.getCinemaId());
        if (cinema == null) throw new MovieFrameException("影院不存在");

        Hall hall = new Hall();
        hall.setCinemaId(dto.getCinemaId());
        hall.setName(dto.getName());
        hall.setType(dto.getType() != null ? dto.getType() : 1);
        // 默认 10×10（商家后续可通过批量生成座位添加更多行/列）
        hall.setRowCount(dto.getRowCount() != null ? dto.getRowCount() : 10);
        hall.setColCount(dto.getColCount() != null ? dto.getColCount() : 10);
        hall.setLayout(dto.getLayout());
        hall.setCreatedAt(new Date());
        save(hall);
        log.info("新增影厅 id={} cinemaId={} name={}", hall.getId(), dto.getCinemaId(), dto.getName());
        return hall;
    }

    // ==================== 2. 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, HallSaveDTO dto) {
        Hall hall = getById(id);
        if (hall == null) throw new MovieFrameException("影厅不存在");
        if (dto.getCinemaId() != null) {
            if (cinemaMapper.selectById(dto.getCinemaId()) == null) {
                throw new MovieFrameException("影院不存在");
            }
            hall.setCinemaId(dto.getCinemaId());
        }
        if (StrUtil.isNotBlank(dto.getName())) hall.setName(dto.getName());
        if (dto.getType() != null) hall.setType(dto.getType());
        if (dto.getRowCount() != null) hall.setRowCount(dto.getRowCount());
        if (dto.getColCount() != null) hall.setColCount(dto.getColCount());
        if (dto.getLayout() != null) hall.setLayout(dto.getLayout());
        updateById(hall);
        log.info("修改影厅 id={}", id);
    }

    // ==================== 3. 删除（有场次则拒绝） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Hall hall = getById(id);
        if (hall == null) throw new MovieFrameException("影厅不存在");
        Long refCount = scheduleMapper.selectCount(
                new LambdaQueryWrapper<Schedule>().eq(Schedule::getHallId, id));
        if (refCount != null && refCount > 0) {
            throw new MovieFrameException("该影厅存在 " + refCount + " 场排片，无法删除");
        }
        removeById(id);
        log.info("删除影厅 id={}", id);
    }

    // ==================== 4. 分页查询 ====================

    @Override
    public Page<HallVO> pageQuery(HallPageQuery query) {
        LambdaQueryWrapper<Hall> wrapper = new LambdaQueryWrapper<>();
        if (query.getCinemaId() != null) wrapper.eq(Hall::getCinemaId, query.getCinemaId());
        if (StrUtil.isNotBlank(query.getKeyword())) wrapper.like(Hall::getName, query.getKeyword());
        if (query.getType() != null) wrapper.eq(Hall::getType, query.getType());
        wrapper.orderByDesc(Hall::getCinemaId).orderByAsc(Hall::getName);

        Page<Hall> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Page<HallVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        // 批量查影院名称
        List<Long> cinemaIds = entityPage.getRecords().stream().map(Hall::getCinemaId).distinct().collect(Collectors.toList());
        Map<Long, Cinema> cinemaMap = cinemaMapper.selectBatchIds(cinemaIds).stream()
                .collect(Collectors.toMap(Cinema::getId, Function.identity()));

        voPage.setRecords(entityPage.getRecords().stream().map(h -> {
            Cinema cinema = cinemaMap.get(h.getCinemaId());
            return HallVO.builder()
                    .id(h.getId())
                    .cinemaId(h.getCinemaId())
                    .cinemaName(cinema != null ? cinema.getName() : "")
                    .name(h.getName())
                    .type(h.getType())
                    .typeDesc(HALL_TYPE_DESC.getOrDefault(h.getType(), "其他"))
                    .rowCount(h.getRowCount())
                    .colCount(h.getColCount())
                    .totalSeat(h.getRowCount() * h.getColCount())
                    .layout(h.getLayout())
                    .createdAt(FMT.format(LocalDateTime.ofInstant(h.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                    .build();
        }).collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 5. 详情 ====================

    @Override
    public HallVO detail(Long id) {
        Hall hall = getById(id);
        if (hall == null) throw new MovieFrameException("影厅不存在");
        Cinema cinema = cinemaMapper.selectById(hall.getCinemaId());
        return HallVO.builder()
                .id(hall.getId())
                .cinemaId(hall.getCinemaId())
                .cinemaName(cinema != null ? cinema.getName() : "")
                .name(hall.getName())
                .type(hall.getType())
                .typeDesc(HALL_TYPE_DESC.getOrDefault(hall.getType(), "其他"))
                .rowCount(hall.getRowCount())
                .colCount(hall.getColCount())
                .totalSeat(hall.getRowCount() * hall.getColCount())
                .layout(hall.getLayout())
                .createdAt(FMT.format(LocalDateTime.ofInstant(hall.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                .build();
    }
}
