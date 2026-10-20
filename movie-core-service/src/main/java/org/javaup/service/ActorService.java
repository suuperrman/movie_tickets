package org.javaup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.dto.actor.ActorPageQuery;
import org.javaup.dto.actor.ActorSaveDTO;
import org.javaup.dto.actor.ActorVO;
import org.javaup.entity.Actor;

/**
 * 演员模块服务接口
 */
public interface ActorService extends IService<Actor> {

    Actor create(ActorSaveDTO dto);
    void update(Long id, ActorSaveDTO dto);
    void delete(Long id);
    void updateStatus(Long id, Integer status);
    Page<ActorVO> pageQuery(ActorPageQuery query);
    ActorVO detail(Long id);
}
