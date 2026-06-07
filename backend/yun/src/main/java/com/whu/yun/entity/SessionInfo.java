package com.whu.yun.entity;

import com.whu.yun.dto.SessionTimeRangeDto;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    
    private Long id;
    private Long sessionCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SessionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum SessionStatus {
        ACTIVE, COMPLETED
    }
    
    // 业务方法
    public void complete(LocalDateTime endTime) {
        this.endTime = endTime;
        this.status = SessionStatus.COMPLETED;
    }
    
    public String getTimeRangeDisplay() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (startTime == null) return "";
        if (endTime == null) return startTime.format(formatter) + " ~ 进行中";
        return startTime.format(formatter) + " ~ " + endTime.format(formatter);
    }
    
    // 转换为DTO
    public SessionTimeRangeDto toSessionTimeRangeDto() {
        return new SessionTimeRangeDto(sessionCount, startTime, endTime);
    }
}