package com.whu.yun.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import java.util.List;

/**
 * 用于包装分页查询结果的 DTO
 */
@Data
public class PageResultDto<T> {
    private long total; // 总记录数
    private long pages;  // 总页数
    private List<T> list; // 当前页的数据列表

    /**
     * 从MyBatis-Plus的IPage转换
     */
    public static <T> PageResultDto<T> fromIPage(IPage<T> page) {
        PageResultDto<T> result = new PageResultDto<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(page.getRecords());
        return result;
    }
}