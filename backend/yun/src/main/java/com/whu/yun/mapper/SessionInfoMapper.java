package com.whu.yun.mapper;

import com.whu.yun.entity.SessionInfo;
import com.whu.yun.dto.SessionTimeRangeDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SessionInfoMapper {
    
    /**
     * 插入新的会话记录
     */
    int insertSession(SessionInfo sessionInfo);
    
    /**
     * 更新会话记录
     */
    int updateSession(SessionInfo sessionInfo);
    
    /**
     * 根据会话次数查询
     */
    SessionInfo selectBySessionCount(@Param("sessionCount") Long sessionCount);
    
    /**
     * 查询所有会话，按会话次数升序
     */
    List<SessionInfo> selectAllOrderBySessionCountAsc();
    
    /**
     * 根据状态查询会话
     */
    SessionInfo selectByStatus(@Param("status") String status);
    
    /**
     * 获取最大会话次数
     */
    Long selectMaxSessionCount();
    
    /**
     * 直接查询会话时间区间DTO（用于前端显示）
     */
    List<SessionTimeRangeDto> selectAllSessionTimeRanges();
    
    /**
     * 根据会话次数查询时间区间
     */
    SessionTimeRangeDto selectSessionTimeRangeByCount(@Param("sessionCount") Long sessionCount);
    
    /**
     * 根据ID删除会话（管理功能）
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 根据会话次数删除会话（管理功能）
     */
    int deleteBySessionCount(@Param("sessionCount") Long sessionCount);
}