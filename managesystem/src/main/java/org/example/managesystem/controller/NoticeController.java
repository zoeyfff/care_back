package org.example.managesystem.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Notice;
import org.example.managesystem.entity.NoticeFile;
import org.example.managesystem.mapper.NoticeMapper;
import org.example.managesystem.mapper.NoticeFileMapper;
import org.example.managesystem.security.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeMapper noticeMapper;
    private final NoticeFileMapper noticeFileMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.notice.max-file-size-mb:20}")
    private long maxFileSizeMb;

    private static final List<String> ALLOWED_EXT = Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "png", "jpg", "jpeg", "zip"
    );

    public NoticeController(NoticeMapper noticeMapper, NoticeFileMapper noticeFileMapper, JdbcTemplate jdbcTemplate) {
        this.noticeMapper = noticeMapper;
        this.noticeFileMapper = noticeFileMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type
    ) {
        int p = page == null || page < 1 ? 1 : page;
        int s = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);

        StringBuilder where = new StringBuilder(" WHERE n.is_deleted = 0");
        List<Object> params = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (n.title LIKE ? OR n.content LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw);
            params.add(kw);
        }
        if (StringUtils.hasText(type)) {
            where.append(" AND n.type = ?");
            params.add(type.trim());
        }

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notice n" + where, params.toArray(), Integer.class);
        if (total == null) total = 0;

        String sql = "SELECT n.id, n.title, " +
                "CASE WHEN CHAR_LENGTH(n.content) > 160 THEN CONCAT(SUBSTRING(n.content,1,160),'...') ELSE n.content END AS content, " +
                "n.type, n.publish_status, n.publisher_id, n.publisher_name, " +
                "DATE_FORMAT(n.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                "COUNT(nf.id) AS file_count " +
                "FROM notice n " +
                "LEFT JOIN notice_file nf ON nf.notice_id = n.id AND nf.is_deleted = 0 " +
                where +
                " GROUP BY n.id " +
                " ORDER BY n.create_time DESC LIMIT ? OFFSET ?";
        List<Object> listArgs = new ArrayList<>(params);
        listArgs.add(s);
        listArgs.add((p - 1) * s);

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, listArgs.toArray());
        return ApiResponse.success(ListPage.of(list, total));
    }

    @PostMapping
    public ApiResponse<Notice> add(@RequestBody Notice notice) {
        if (!StringUtils.hasText(notice.getTitle()) || !StringUtils.hasText(notice.getContent())) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "title/content 为必填");
        }
        if (notice.getTitle().length() > 200) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "title 长度不能超过 200");
        }
        if (notice.getContent().length() > 5000) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "content 长度不能超过 5000");
        }
        LoginUser loginUser = currentUser();
        if (loginUser == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        notice.setPublisherId(loginUser.getId());
        notice.setPublisherName(resolveRealName(loginUser));
        if (notice.getPublishStatus() == null) {
            notice.setPublishStatus(1);
        }
        notice.setIsDeleted(0);
        LocalDateTime now = LocalDateTime.now();
        notice.setCreateTime(now);
        notice.setUpdateTime(now);
        noticeMapper.insert(notice);
        return ApiResponse.success(noticeMapper.selectById(notice.getId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, content, type, publish_status, publisher_id, publisher_name, " +
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                        "DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time " +
                        "FROM notice WHERE id = ? AND is_deleted = 0",
                id
        );
        if (rows.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "公告不存在");
        }
        Map<String, Object> data = rows.get(0);
        List<Map<String, Object>> files = jdbcTemplate.queryForList(
                "SELECT id, notice_id, file_name, file_size, uploader_name, " +
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM notice_file WHERE notice_id = ? AND is_deleted = 0 ORDER BY create_time DESC",
                id
        );
        data.put("files", files);
        return ApiResponse.success(data);
    }

    @PutMapping("/{id}")
    public ApiResponse<Notice> update(@PathVariable Long id, @RequestBody Notice notice) {
        Notice old = noticeMapper.selectById(id);
        if (old == null || (old.getIsDeleted() != null && old.getIsDeleted() == 1)) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "公告不存在");
        }
        if (StringUtils.hasText(notice.getTitle()) && notice.getTitle().length() > 200) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "title 长度不能超过 200");
        }
        if (StringUtils.hasText(notice.getContent()) && notice.getContent().length() > 5000) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "content 长度不能超过 5000");
        }
        notice.setId(id);
        notice.setPublisherId(old.getPublisherId());
        notice.setPublisherName(old.getPublisherName());
        notice.setUpdateTime(LocalDateTime.now());
        notice.setIsDeleted(0);
        noticeMapper.updateById(notice);
        return ApiResponse.success(noticeMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        UpdateWrapper<Notice> update = new UpdateWrapper<>();
        update.eq("id", id).set("is_deleted", 1).set("update_time", LocalDateTime.now());
        noticeMapper.update(null, update);
        return ApiResponse.success();
    }

    @PostMapping("/{noticeId}/files")
    public ApiResponse<Map<String, Object>> uploadFile(@PathVariable Long noticeId, @RequestParam("file") MultipartFile file) throws IOException {
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null || (notice.getIsDeleted() != null && notice.getIsDeleted() == 1)) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "公告不存在");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "文件不能为空");
        }
        long maxBytes = Math.max(1, maxFileSizeMb) * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "文件大小不能超过 " + maxFileSizeMb + "MB");
        }

        String rawName = sanitizeFilename(file.getOriginalFilename());
        String ext = getExt(rawName);
        if (!ALLOWED_EXT.contains(ext)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "文件类型不支持，仅允许: " + String.join("/", ALLOWED_EXT));
        }

        LoginUser loginUser = currentUser();
        if (loginUser == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        String uploaderName = resolveRealName(loginUser);

        Path dir = Paths.get(uploadDir, "notices", String.valueOf(noticeId));
        Files.createDirectories(dir);
        String saved = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = dir.resolve(saved);
        file.transferTo(target.toFile());

        NoticeFile entity = new NoticeFile();
        entity.setNoticeId(noticeId);
        entity.setFileName(rawName);
        entity.setFileExt(ext);
        entity.setFileSize(file.getSize());
        entity.setContentType(file.getContentType());
        entity.setStorageType("local");
        entity.setFileKey("notices/" + noticeId + "/" + saved);
        entity.setFileHash(sha256(target));
        entity.setUploaderId(loginUser.getId());
        entity.setUploaderName(uploaderName);
        entity.setCreateTime(LocalDateTime.now());
        entity.setIsDeleted(0);

        try {
            noticeFileMapper.insert(entity);
        } catch (Exception ex) {
            Files.deleteIfExists(target);
            return ApiResponse.fail(ApiCodes.CONFLICT, "附件保存失败：" + ex.getMessage());
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, notice_id, file_name, file_size, uploader_name, " +
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM notice_file WHERE id = ?",
                entity.getId()
        );
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }

    @GetMapping("/{noticeId}/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long noticeId, @PathVariable Long fileId) {
        NoticeFile file = noticeFileMapper.selectById(fileId);
        if (file == null || file.getIsDeleted() != null && file.getIsDeleted() == 1 || !noticeId.equals(file.getNoticeId())) {
            return ResponseEntity.notFound().build();
        }
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(file.getFileKey()).normalize();
        if (!resolved.startsWith(root)) {
            return ResponseEntity.status(403).build();
        }
        File diskFile = resolved.toFile();
        if (!diskFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(diskFile);

        // 使用 try-catch 处理编码异常
        String encoded;
        try {
            encoded = URLEncoder.encode(file.getFileName(), "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encoded = file.getFileName();
        }

        MediaType mediaType = StringUtils.hasText(file.getContentType())
                ? MediaType.parseMediaType(file.getContentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(resource);
    }

    @DeleteMapping("/{noticeId}/files/{fileId}")
    public ApiResponse<Void> deleteFile(@PathVariable Long noticeId, @PathVariable Long fileId) {
        NoticeFile file = noticeFileMapper.selectById(fileId);
        if (file == null || file.getIsDeleted() != null && file.getIsDeleted() == 1 || !noticeId.equals(file.getNoticeId())) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "附件不存在");
        }
        UpdateWrapper<NoticeFile> update = new UpdateWrapper<>();
        update.eq("id", fileId).set("is_deleted", 1);
        noticeFileMapper.update(null, update);
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(file.getFileKey()));
        } catch (Exception ignored) {
        }
        return ApiResponse.success();
    }

    private LoginUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }

    private String resolveRealName(LoginUser loginUser) {
        if (loginUser == null) return "";
        if (StringUtils.hasText(loginUser.getRealName())) return loginUser.getRealName();
        List<String> rows = jdbcTemplate.queryForList("SELECT real_name FROM `user` WHERE id = ?", String.class, loginUser.getId());
        if (!rows.isEmpty() && StringUtils.hasText(rows.get(0))) {
            return rows.get(0);
        }
        return loginUser.getUsername();
    }

    private String sanitizeFilename(String original) {
        String name = original == null ? "file" : Paths.get(original).getFileName().toString();
        if (!StringUtils.hasText(name)) return "file";
        return name.replaceAll("[\\r\\n]", "_");
    }

    private String getExt(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) return "";
        return fileName.substring(idx + 1).toLowerCase();
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file);
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
