package org.javaup.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.cinema.CinemaPageQuery;
import org.javaup.dto.cinema.CinemaSaveDTO;
import org.javaup.dto.cinema.CinemaVO;
import org.javaup.entity.Cinema;
import org.javaup.entity.Hall;
import org.javaup.entity.Schedule;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.CinemaMapper;
import org.javaup.mapper.HallMapper;
import org.javaup.mapper.ScheduleMapper;
import org.javaup.service.CinemaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 影院模块服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CinemaServiceImpl extends ServiceImpl<CinemaMapper, Cinema> implements CinemaService {

    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final ScheduleMapper scheduleMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ==================== 1. 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Cinema create(CinemaSaveDTO dto) {
        Cinema cinema = new Cinema();
        cinema.setName(dto.getName());
        cinema.setCity(dto.getCity());
        cinema.setAddress(dto.getAddress());
        cinema.setPhone(dto.getPhone());
        cinema.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        cinema.setCreatedAt(new Date());
        save(cinema);
        log.info("新增影院 id={} name={}", cinema.getId(), cinema.getName());
        return cinema;
    }

    // ==================== 2. 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CinemaSaveDTO dto) {
        Cinema cinema = getById(id);
        if (cinema == null) throw new MovieFrameException("影院不存在");
        if (StrUtil.isNotBlank(dto.getName())) cinema.setName(dto.getName());
        if (StrUtil.isNotBlank(dto.getCity())) cinema.setCity(dto.getCity());
        if (StrUtil.isNotBlank(dto.getAddress())) cinema.setAddress(dto.getAddress());
        if (StrUtil.isNotBlank(dto.getPhone())) cinema.setPhone(dto.getPhone());
        if (dto.getStatus() != null) cinema.setStatus(dto.getStatus());
        updateById(cinema);
        log.info("修改影院 id={}", id);
    }

    // ==================== 3. 删除（有影厅/场次则拒绝） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Cinema cinema = getById(id);
        if (cinema == null) throw new MovieFrameException("影院不存在");

        Long hallCount = hallMapper.selectCount(new LambdaQueryWrapper<Hall>().eq(Hall::getCinemaId, id));
        if (hallCount != null && hallCount > 0) {
            throw new MovieFrameException("该影院存在 " + hallCount + " 个影厅，无法删除");
        }
        Long scheduleCount = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>().eq(Schedule::getCinemaId, id));
        if (scheduleCount != null && scheduleCount > 0) {
            throw new MovieFrameException("该影院存在 " + scheduleCount + " 场排片，无法删除");
        }
        removeById(id);
        log.info("删除影院 id={}", id);
    }

    // ==================== 4. 启停用 ====================

    @Override
    public void updateStatus(Long id, Integer status) {
        Cinema cinema = getById(id);
        if (cinema == null) throw new MovieFrameException("影院不存在");
        cinema.setStatus(status);
        updateById(cinema);
        log.info("影院启停用 id={} status={}", id, status);
    }

    // ==================== 5. 分页查询 ====================

    @Override
    public Page<CinemaVO> pageQuery(CinemaPageQuery query) {
        LambdaQueryWrapper<Cinema> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.like(Cinema::getName, query.getKeyword());
        }
        if (StrUtil.isNotBlank(query.getCity())) {
            wrapper.eq(Cinema::getCity, query.getCity());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Cinema::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Cinema::getCreatedAt);

        Page<Cinema> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Page<CinemaVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        List<Long> ids = entityPage.getRecords().stream().map(Cinema::getId).collect(Collectors.toList());
        Map<Long, Integer> hallCountMap = loadHallCount(ids);

        voPage.setRecords(entityPage.getRecords().stream().map(c -> CinemaVO.builder()
                .id(c.getId()).name(c.getName()).city(c.getCity())
                .address(c.getAddress()).phone(c.getPhone()).status(c.getStatus())
                .hallCount(hallCountMap.getOrDefault(c.getId(), 0))
                .createdAt(FMT.format(LocalDateTime.ofInstant(c.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                .build()).collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 6. 详情 ====================

    @Override
    public CinemaVO detail(Long id) {
        Cinema cinema = getById(id);
        if (cinema == null) throw new MovieFrameException("影院不存在");
        Integer hallCount = loadHallCountSingle(id);
        return CinemaVO.builder()
                .id(cinema.getId()).name(cinema.getName()).city(cinema.getCity())
                .address(cinema.getAddress()).phone(cinema.getPhone()).status(cinema.getStatus())
                .hallCount(hallCount)
                .createdAt(FMT.format(LocalDateTime.ofInstant(cinema.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                .build();
    }

    // ==================== 内部辅助 ====================

    private Map<Long, Integer> loadHallCount(List<Long> cinemaIds) {
        if (cinemaIds == null || cinemaIds.isEmpty()) return Map.of();
        QueryWrapper<Hall> qw = new QueryWrapper<>();
        qw.select("cinema_id", "COUNT(*) AS cnt").in("cinema_id", cinemaIds).groupBy("cinema_id");
        return hallMapper.selectMaps(qw).stream().collect(Collectors.toMap(
                r -> ((Number) r.get("cinema_id")).longValue(),
                r -> ((Number) r.get("cnt")).intValue()));
    }

    private Integer loadHallCountSingle(Long cinemaId) {
        QueryWrapper<Hall> qw = new QueryWrapper<>();
        qw.select("COUNT(*) AS cnt").eq("cinema_id", cinemaId);
        Map<String, Object> row = hallMapper.selectMaps(qw).stream().findFirst().orElse(null);
        return row != null ? ((Number) row.get("cnt")).intValue() : 0;
    }
}
