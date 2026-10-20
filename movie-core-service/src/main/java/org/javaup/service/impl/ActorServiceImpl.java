package org.javaup.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.javaup.dto.actor.ActorPageQuery;
import org.javaup.dto.actor.ActorSaveDTO;
import org.javaup.dto.actor.ActorVO;
import org.javaup.entity.Actor;
import org.javaup.entity.MovieActorRel;
import org.javaup.exception.MovieFrameException;
import org.javaup.mapper.ActorMapper;
import org.javaup.mapper.MovieActorRelMapper;
import org.javaup.service.ActorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 演员模块服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActorServiceImpl extends ServiceImpl<ActorMapper, Actor> implements ActorService {

    private final ActorMapper actorMapper;
    private final MovieActorRelMapper actorRelMapper;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ==================== 1. 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Actor create(ActorSaveDTO dto) {
        Actor actor = new Actor();
        actor.setName(dto.getName());
        actor.setAvatarUrl(dto.getAvatarUrl());
        actor.setGender(dto.getGender() != null ? dto.getGender() : 0);
        actor.setIntro(dto.getIntro() != null ? dto.getIntro() : "");
        actor.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        actor.setCreatedAt(new Date());
        actor.setUpdatedAt(new Date());
        save(actor);
        log.info("新增演员 id={} name={}", actor.getId(), actor.getName());
        return actor;
    }

    // ==================== 2. 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ActorSaveDTO dto) {
        Actor actor = getById(id);
        if (actor == null) {
            throw new MovieFrameException("演员不存在");
        }
        if (StrUtil.isNotBlank(dto.getName())) actor.setName(dto.getName());
        if (StrUtil.isNotBlank(dto.getAvatarUrl())) actor.setAvatarUrl(dto.getAvatarUrl());
        if (dto.getGender() != null) actor.setGender(dto.getGender());
        if (dto.getIntro() != null) actor.setIntro(dto.getIntro());
        if (dto.getStatus() != null) actor.setStatus(dto.getStatus());
        actor.setUpdatedAt(new Date());
        updateById(actor);
        log.info("修改演员 id={}", id);
    }

    // ==================== 3. 删除（被电影引用则拒绝） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Actor actor = getById(id);
        if (actor == null) {
            throw new MovieFrameException("演员不存在");
        }
        Long refCount = actorRelMapper.selectCount(
                new LambdaQueryWrapper<MovieActorRel>().eq(MovieActorRel::getActorId, id));
        if (refCount != null && refCount > 0) {
            throw new MovieFrameException("该演员已参演 " + refCount + " 部电影，无法删除");
        }
        removeById(id);
        log.info("删除演员 id={}", id);
    }

    // ==================== 4. 启停用 ====================

    @Override
    public void updateStatus(Long id, Integer status) {
        Actor actor = getById(id);
        if (actor == null) {
            throw new MovieFrameException("演员不存在");
        }
        actor.setStatus(status);
        actor.setUpdatedAt(new Date());
        updateById(actor);
        log.info("演员启停用 id={} status={}", id, status);
    }

    // ==================== 5. 分页查询 ====================

    @Override
    public Page<ActorVO> pageQuery(ActorPageQuery query) {
        LambdaQueryWrapper<Actor> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.like(Actor::getName, query.getKeyword());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Actor::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(Actor::getCreatedAt);

        Page<Actor> entityPage = page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        Page<ActorVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());

        List<Long> ids = entityPage.getRecords().stream().map(Actor::getId).collect(Collectors.toList());
        Map<Long, Integer> movieCountMap = loadMovieCount(ids);

        voPage.setRecords(entityPage.getRecords().stream().map(a -> ActorVO.builder()
                .id(a.getId()).name(a.getName()).avatarUrl(a.getAvatarUrl())
                .gender(a.getGender()).intro(a.getIntro()).status(a.getStatus())
                .movieCount(movieCountMap.getOrDefault(a.getId(), 0))
                .createdAt(FMT.format(LocalDateTime.ofInstant(a.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                .build()).collect(Collectors.toList()));
        return voPage;
    }

    // ==================== 6. 详情 ====================

    @Override
    public ActorVO detail(Long id) {
        Actor actor = getById(id);
        if (actor == null) {
            throw new MovieFrameException("演员不存在");
        }
        Integer movieCount = loadMovieCountSingle(id);
        return ActorVO.builder()
                .id(actor.getId()).name(actor.getName()).avatarUrl(actor.getAvatarUrl())
                .gender(actor.getGender()).intro(actor.getIntro()).status(actor.getStatus())
                .movieCount(movieCount)
                .createdAt(FMT.format(LocalDateTime.ofInstant(actor.getCreatedAt().toInstant(), ZoneId.systemDefault())))
                .build();
    }

    // ==================== 内部辅助 ====================

    private Map<Long, Integer> loadMovieCount(List<Long> actorIds) {
        if (actorIds == null || actorIds.isEmpty()) return Map.of();
        QueryWrapper<MovieActorRel> qw = new QueryWrapper<>();
        qw.select("actor_id", "COUNT(*) AS cnt")
                .in("actor_id", actorIds)
                .groupBy("actor_id");
        List<Map<String, Object>> rows = actorRelMapper.selectMaps(qw);
        return rows.stream().collect(Collectors.toMap(
                r -> ((Number) r.get("actor_id")).longValue(),
                r -> ((Number) r.get("cnt")).intValue()));
    }

    private Integer loadMovieCountSingle(Long actorId) {
        QueryWrapper<MovieActorRel> qw = new QueryWrapper<>();
        qw.select("COUNT(*) AS cnt").eq("actor_id", actorId);
        Map<String, Object> row = actorRelMapper.selectMaps(qw).stream().findFirst().orElse(null);
        return row != null ? ((Number) row.get("cnt")).intValue() : 0;
    }
}
