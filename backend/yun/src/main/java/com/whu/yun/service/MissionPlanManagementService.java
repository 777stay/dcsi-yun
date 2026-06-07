package com.whu.yun.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.yun.dto.PageDto;
import com.whu.yun.entity.MissionPlanEntity;
import com.whu.yun.mapper.MissionPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

@Service
@Slf4j
@RequiredArgsConstructor
public class MissionPlanManagementService {

    @Autowired
    private  MissionPlanMapper missionPlanMapper;

    /**
     * 分页获取所有任务规划记录。
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @return 分页后的任务规划信息
     */
    public PageDto<MissionPlanEntity> getMissionPlans(int pageNum, int pageSize) {
        Page<MissionPlanEntity> page = new Page<>(pageNum, pageSize);
        IPage<MissionPlanEntity> result = missionPlanMapper.findAll(page);
        return PageDto.fromIPage(result);
    }

    /**
     * 删除一个任务规划记录及其关联的KML文件目录。
     * @param id 任务ID
     */
    @Transactional // 确保数据库操作和文件操作要么都成功，要么都失败
    public void deleteMissionPlan(Long id) {
        // 1. 从数据库中查找记录，以获取文件路径
        MissionPlanEntity missionPlan = missionPlanMapper.findById(id);
        if (missionPlan == null) {
            throw new RuntimeException("未找到ID为 " + id + " 的任务规划记录。");
        }

        // 2. 删除物理文件目录
        String kmlDirectoryPath = missionPlan.getKmlFilePath();
        if (kmlDirectoryPath != null && !kmlDirectoryPath.isEmpty()) {
            try {
                Path directory = Paths.get(kmlDirectoryPath);
                if (Files.exists(directory)) {
                    // 递归删除目录及其所有内容
                    Files.walk(directory)
                         .sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(java.io.File::delete);
                    log.info("已成功删除KML文件目录: {}", kmlDirectoryPath);
                }
            } catch (IOException e) {
                log.error("删除KML文件目录失败: {}", kmlDirectoryPath, e);
                // 抛出异常以触发事务回滚
                throw new RuntimeException("删除KML文件失败。", e);
            }
        }

        // 3. 从数据库中删除记录
        missionPlanMapper.deleteById(id);
        log.info("已成功从数据库中删除ID为 {} 的任务规划记录。", id);
    }
    /**
     * 【新增】准备任务规划的 KML 文件以供下载。
     * @param id 任务ID
     * @return 包含 ZIP 文件流的 Spring Resource
     * @throws IOException 如果文件读取或打包失败
     */
    public Resource prepareKmlDownload(Long id) throws IOException {
        // 1. 查找任务记录
        MissionPlanEntity missionPlan = missionPlanMapper.findById(id);
        if (missionPlan == null || missionPlan.getKmlFilePath() == null) {
            throw new RuntimeException("未找到任务记录或关联的KML路径。");
        }

        Path kmlDirectory = Paths.get(missionPlan.getKmlFilePath());
        if (!Files.isDirectory(kmlDirectory)) {
            throw new RuntimeException("KML路径不是一个有效的目录: " + kmlDirectory);
        }

        // 2. 创建一个临时的 ZIP 文件
        Path tempZipPath = Files.createTempFile("mission_" + id + "_", ".zip");

        // 3. 使用 Zip4j 将目录下的所有 .kml 和 .kmz 文件添加到 ZIP 包中
        try (ZipFile zipFile = new ZipFile(tempZipPath.toFile())) {
            Files.walk(kmlDirectory)
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        String fileName = path.toString().toLowerCase();
                        return fileName.endsWith(".kml") || fileName.endsWith(".kmz");
                    })
                    .forEach(path -> {
                        try {
                            zipFile.addFile(path.toFile());
                        } catch (IOException e) {
                            throw new RuntimeException("添加到ZIP包失败: " + path, e);
                        }
                    });
        }

        log.info("已成功创建临时ZIP包: {}", tempZipPath);

        // 4. 将临时 ZIP 文件包装成 Spring 的 Resource 以便返回
        // Spring 会在响应发送完毕后自动处理这个 InputStream
        return new InputStreamResource(new FileInputStream(tempZipPath.toFile()));
    }
}
