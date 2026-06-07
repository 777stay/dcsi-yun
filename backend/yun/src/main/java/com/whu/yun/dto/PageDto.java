package com.whu.yun.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 分页数据传输对象
 * 将MyBatis-Plus的IPage转换为前端期望的格式
 * 保持与PageHelper完全一致的返回格式
 */
@Data
public class PageDto<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int pageNum;        // 当前页码
    private int pageSize;       // 每页数量
    private int size;           // 当前页实际数量
    private long total;         // 总记录数
    private int pages;          // 总页数
    private List<T> list;       // 数据列表

    // 导航相关字段（可选）
    private boolean firstPage;
    private boolean lastPage;
    private boolean previousPage;
    private boolean nextPage;

    /**
     * 从MyBatis-Plus的IPage转换
     */
    public static <T> PageDto<T> fromIPage(IPage<T> page) {
        PageDto<T> dto = new PageDto<>();
        dto.setPageNum((int) page.getCurrent());
        dto.setPageSize((int) page.getSize());
        dto.setSize(page.getRecords().size());
        dto.setTotal(page.getTotal());
        dto.setPages((int) page.getPages());
        dto.setList(page.getRecords());
        
        // 设置导航字段
        dto.setFirstPage(page.getCurrent() == 1);
        dto.setLastPage(page.getCurrent() >= page.getPages());
        dto.setPreviousPage(page.getCurrent() > 1);
        dto.setNextPage(page.getCurrent() < page.getPages());
        
        return dto;
    }
}
