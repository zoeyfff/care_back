package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nurse")
public class NurseMedicationController {

    private final JdbcTemplate jdbcTemplate;

    public NurseMedicationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/medications")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" AND mr.status = ?");
            args.add(status);
        }

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM medication_record mr" + where, args.toArray(), Integer.class);
        if (total == null) total = 0;

        StringBuilder sql = new StringBuilder(
                "SELECT mr.id, mr.elder_id, e.name AS elder_name, mr.medicine_name, mr.dosage, " +
                        "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, " +
                        "mr.remark, mr.status, mr.execute_user, u.real_name AS execute_user_name, " +
                        "DATE_FORMAT(mr.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, " +
                        "DATE_FORMAT(mr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM medication_record mr " +
                        "LEFT JOIN elder e ON mr.elder_id = e.id " +
                        "LEFT JOIN `user` u ON mr.execute_user = u.id " +
                        where + " ORDER BY mr.take_time DESC, mr.id DESC"
        );
        if (page != null && size != null && page > 0 && size > 0) {
            sql.append(" LIMIT ? OFFSET ?");
            args.add(size);
            args.add((page - 1) * size);
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, total));
    }

    @PatchMapping("/medications/{id}/done")
    public ApiResponse<Map<String, Object>> done(@PathVariable Long id) {
        Long uid = currentUserId();
        if (uid == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        int updated = jdbcTemplate.update(
                "UPDATE medication_record SET status = 1, execute_user = ?, execute_time = ? WHERE id = ?",
                uid, LocalDateTime.now(), id
        );
        if (updated <= 0) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "记录不存在");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mr.id, mr.elder_id, e.name AS elder_name, mr.medicine_name, mr.dosage, " +
                        "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, " +
                        "mr.remark, mr.status, mr.execute_user, u.real_name AS execute_user_name, " +
                        "DATE_FORMAT(mr.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, " +
                        "DATE_FORMAT(mr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM medication_record mr " +
                        "LEFT JOIN elder e ON mr.elder_id = e.id " +
                        "LEFT JOIN `user` u ON mr.execute_user = u.id WHERE mr.id = ?",
                id
        );
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return ((LoginUser) principal).getId();
        }
        return null;
    }
}

