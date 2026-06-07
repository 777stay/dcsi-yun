package com.whu.yun.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.yun.entity.ImageEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


public interface ImageMapper extends BaseMapper<ImageEntity> {
    /**
     * 插入一条图片数据记录
     * @param imageEntity 包含图片元数据的实体
     * @return 返回影响的行数
     */
    int insertImage(ImageEntity imageEntity);

    /**
     * 根据ID查询图片信息
     * @param id 图片记录的ID
     * @return 返回图片实体，如果不存在则返回null
     */
    ImageEntity findById(Long id);

    /**
     * 根据ID删除图片记录
     * @param id 图片记录的ID
     * @return 返回影响的行数
     */
    int deleteById(Long id);

    List<ImageEntity> findByRobotId(String robotId);
}
