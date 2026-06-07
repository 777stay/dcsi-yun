package com.whu.yun.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionTimeRangeDto {
    private Long receptionCount;
    private LocalDateTime minTime;
    private LocalDateTime maxTime;
    private String timeRangeDisplay;
    
    public SessionTimeRangeDto(Long receptionCount, LocalDateTime startTime, LocalDateTime endTime) {
        this.receptionCount = receptionCount;
        this.minTime = startTime;
        this.maxTime = endTime;
        this.timeRangeDisplay = formatTimeRange(startTime, endTime);
    }
    
    private String formatTimeRange(LocalDateTime start, LocalDateTime end) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (start == null && end == null) return "";
        if (start == null) return "~ " + end.format(formatter);
        if (end == null) return start.format(formatter) + " ~ 进行中";
        return start.format(formatter) + " ~ " + end.format(formatter);
    }
}