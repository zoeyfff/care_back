package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.FileInfo;
import org.example.managesystem.mapper.FileInfoMapper;
import org.example.managesystem.security.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    private final FileInfoMapper fileInfoMapper;
    private final JdbcTemplate jdbcTemplate;

    public FileController(FileInfoMapper fileInfoMapper, JdbcTemplate jdbcTemplate) {
        this.fileInfoMapper = fileInfoMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        String sql = "SELECT f.id, f.file_name, f.file_url, f.file_size, f.upload_user, "
                + "DATE_FORMAT(f.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                + "u.real_name AS uploader_name FROM file_info f "
                + "LEFT JOIN `user` u ON f.upload_user = u.id ORDER BY f.create_time DESC";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping("/upload")
    public ApiResponse<FileInfo> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "文件不能为空");
        }
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String ext = "";
        String original = file.getOriginalFilename();
        if (StringUtils.hasText(original) && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        String savedName = UUID.randomUUID() + ext;
        Path target = dir.resolve(savedName);
        file.transferTo(target.toFile());

        Long userId = null;
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            userId = ((LoginUser) principal).getId();
        }

        FileInfo info = new FileInfo();
        info.setFileName(original);
        info.setFileUrl(savedName);
        info.setFileSize(file.getSize());
        info.setUploadUser(userId);
        info.setCreateTime(LocalDateTime.now());
        fileInfoMapper.insert(info);
        return ApiResponse.success(info);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileInfo info = fileInfoMapper.selectById(id);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        File file = Paths.get(uploadDir, info.getFileUrl()).toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + info.getFileName() + "\"")
                .body(resource);
    }
}
