package com.whu.yun.dto;


import com.whu.yun.entity.ImageEntity;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ImageDto {
    private Long id;
    private String robotId;
    private String format;
    private int width;
    private int height;
    private String filePath;
    private LocalDateTime timestamp;

    public static ImageDto fromEntity(ImageEntity entity) {
        if (entity == null) return null;
        ImageDto dto = new ImageDto();
        dto.setId(entity.getId());
        dto.setRobotId(entity.getRobotId());
        dto.setFormat(entity.getFormat());
        dto.setWidth(entity.getWidth());
        dto.setHeight(entity.getHeight());
        dto.setFilePath(entity.getFilePath());
        dto.setTimestamp(entity.getTimestamp());
        return dto;
    }
}