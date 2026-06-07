package com.whu.yun.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReceptionCountWithTimeRange {
    private Long receptionCount;
    private LocalDateTime minTime;
    private LocalDateTime maxTime;
    
    // 格式化时间区间显示
    public String getTimeRangeDisplay() {
        if (minTime == null || maxTime == null) {
            return "";
        }
        return String.format("%s ~ %s", 
            minTime.toString().replace("T", " "), 
            maxTime.toString().replace("T", " "));
    }
}