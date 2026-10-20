package org.javaup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.seat.SeatPageQuery;
import org.javaup.dto.seat.SeatSaveDTO;
import org.javaup.dto.seat.SeatVO;
import org.javaup.entity.Hall;
import org.javaup.entity.ScheduleSeat;
import org.javaup.entity.Seat;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.HallMapper;
import org.javaup.mapper.ScheduleSeatMapper;
import org.javaup.mapper.SeatMapper;
import org.javaup.service.SeatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 物理座位模块服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatServiceImpl extends ServiceImpl<SeatMapper, Seat> implements SeatService {

    private final SeatMapper seatMapper;
    private final HallMapper hallMapper;
    private final ScheduleSeatMapper scheduleSeatMapper;

    // ==================== 1. 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Seat create(SeatSaveDTO dto) {
        Hall hall = hallMapper.selectById(dto.getHallId());
        if (hall == null) throw new MovieFrameException("影厅不存在");
        Seat seat = new Seat();
        seat.setHallId(dto.getHallId());
        seat.setRowNo(dto.getRowNo());
        seat.setColNo(dto.getColNo());
        seat.setSeatCode(dto.getSeatCode() != null ? dto.getSeatCode() : buildSeatCode(dto.getRowNo(), dto.getColNo()));
        seat.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : 1);
        save(seat);
        log.info("新增座位 id={} hallId={} row={} col={}", seat.getId(), dto.getHallId(), dto.getRowNo(), dto.getColNo());
        return seat;
    }

    // ==================== 2. 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SeatSaveDTO dto) {
        Seat seat = getById(id);
        if (seat == null) throw new MovieFrameException("座位不存在");
        if (dto.getHallId() != null) {
            if (hallMapper.selectById(dto.getHallId()) == null) throw new MovieFrameException("影厅不存在");
            seat.setHallId(dto.getHallId());
        }
        if (dto.getRowNo() != null) seat.setRowNo(dto.getRowNo());
        if (dto.getColNo() != null) {
            seat.setColNo(dto.getColNo());
            seat.setSeatCode(dto.getSeatCode() != null ? dto.getSeatCode() : buildSeatCode(seat.getRowNo(), dto.getColNo()));
        }
        if (dto.getSeatCode() != null) seat.setSeatCode(dto.getSeatCode());
        if (dto.getIsActive() != null) seat.setIsActive(dto.getIsActive());
        updateById(seat);
        log.info("修改座位 id={}", id);
    }

    // ==================== 3. 删除（被场次引用则拒绝） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Seat seat = getById(id);
        if (seat == null) throw new MovieFrameException("座位不存在");
        Long ref = scheduleSeatMapper.selectCount(new LambdaQueryWrapper<ScheduleSeat>().eq(ScheduleSeat::getSeatId, id));
        if (ref != null && ref > 0) {
            throw new MovieFrameException("该座位已被 " + ref + " 个场次引用，无法删除");
        }
        removeById(id);
        log.info("删除座位 id={}", id);
    }

    // ==================== 4. 批量生成影厅全部座位 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchGenerate(Long hallId) {
        Hall hall = hallMapper.selectById(hallId);
        if (hall == null) throw new MovieFrameException("影厅不存在");
        // 先清旧的
        seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getHallId, hallId));
        int total = 0;
        for (int r = 1; r <= hall.getRowCount(); r++) {
            for (int c = 1; c <= hall.getColCount(); c++) {
                Seat s = new Seat();
                s.setHallId(hallId);
                s.setRowNo(r);
                s.setColNo(c);
                s.setSeatCode(buildSeatCode(r, c));
                s.setIsActive(1);
                seatMapper.insert(s);
                total++;
            }
        }
        log.info("批量生成座位 hallId={} 总数={}", hallId, total);
        return total;
    }

    // ==================== 5. 分页查询 ====================

    @Override
    public Page<SeatVO> pageQuery(SeatPageQuery query) {
        LambdaQueryWrapper<Seat> wrapper = new LambdaQueryWrapper<>();
        if (query.getHallId() != null) wrapper.eq(Seat::getHallId, query.getHallId());
        if (query.getIsActive() != null) wrapper.eq(Seat::getIsActive, query.getIsActive());
        if (query.getRowNo() != null) wrapper.eq(Seat::getRowNo, query.getRowNo());
        wrapper.orderByAsc(Seat::getHallId);
        wrapper.orderByAsc(Seat::getRowNo);
        wrapper.orderByAsc(Seat::getColNo);

        Page<Seat> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Page<SeatVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        List<Long> hallIds = entityPage.getRecords().stream().map(Seat::getHallId).distinct().collect(Collectors.toList());
        Map<Long, String> hallNameMap = hallMapper.selectBatchIds(hallIds).stream().collect(Collectors.toMap(Hall::getId, Hall::getName));

        voPage.setRecords(entityPage.getRecords().stream().map(s -> SeatVO.builder()
                .id(s.getId())
                .hallId(s.getHallId())
                .hallName(hallNameMap.getOrDefault(s.getHallId(), ""))
                .rowNo(s.getRowNo())
                .colNo(s.getColNo())
                .seatCode(s.getSeatCode())
                .isActive(s.getIsActive())
                .build()).collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 6. 详情 ====================

    @Override
    public SeatVO detail(Long id) {
        Seat s = getById(id);
        if (s == null) {
            throw new MovieFrameException("座位不存在");
        }
        String hallName = hallMapper.selectById(s.getHallId()).getName();
        return SeatVO.builder()
                .id(s.getId()).hallId(s.getHallId()).hallName(hallName)
                .rowNo(s.getRowNo()).colNo(s.getColNo()).seatCode(s.getSeatCode())
                .isActive(s.getIsActive()).build();
    }

    private static String buildSeatCode(int r, int c) {
        return ((char) ('A' + r - 1)) + String.valueOf(c);
    }
}
