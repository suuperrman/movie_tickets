package org.javaup.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.javaup.entity.MovieCategory;

import java.util.List;

/**
 * 电影分类服务接口
 */
public interface MovieCategoryService extends IService<MovieCategory> {

    /**
     * 用户端：上架分类列表（带 Redis 缓存，TTL 1h）
     */
    List<MovieCategory> listForUser();

    /**
     * 管理端：全部分类列表（不走缓存，实时性优先）
     */
    List<MovieCategory> listAll();

    /**
     * 管理端：新增分类（写完后失效缓存）
     */
    MovieCategory createCategory(MovieCategory category);

    /**
     * 管理端：更新分类（写完后失效缓存）
     */
    void updateCategory(Long id, MovieCategory category);

    /**
     * 管理端：删除分类（写完后失效缓存）
     */
    void deleteCategory(Long id);
}
