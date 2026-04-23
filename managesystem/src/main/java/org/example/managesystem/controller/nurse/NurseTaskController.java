package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nurse")
public class NurseTaskController {

    private final JdbcTemplate jdbcTemplate;

    public NurseTaskController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> myTasks(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String room_no,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        LoginUser current = currentUser();
        if (current == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        where.append(" AND ct.assigned_to = ?");
        args.add(current.getId());
        if (status != null) {
            where.append(" AND ct.status = ?");
            args.add(status);
        }
        if (room_no != null && !room_no.trim().isEmpty()) {
            where.append(" AND e.room_no LIKE ?");
            args.add("%" + room_no.trim() + "%");
        }

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_task ct LEFT JOIN elder e ON ct.elder_id = e.id" + where,
                args.toArray(),
                Integer.class
        );
        if (total == null) total = 0;

        StringBuilder sql = new StringBuilder(
                "SELECT ct.id, ct.elder_id, ct.task_name, ct.status, " +
                        "DATE_FORMAT(ct.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, " +
                        "ct.assigned_to, ct.assigned_to_name, ct.frequency_type, ct.priority, " +
                        "DATE_FORMAT(ct.next_execute_time, '%Y-%m-%d %H:%i:%s') AS next_execute_time, " +
                        "DATE_FORMAT(ct.preferred_time, '%H:%i:%s') AS preferred_time, ct.instruction, " +
                        "ct.remark, DATE_FORMAT(ct.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                        "e.name AS elder_name " +
                        "FROM care_task ct LEFT JOIN elder e ON ct.elder_id = e.id" +
                        where + " ORDER BY ct.execute_time DESC, ct.id DESC"
        );
        if (page != null && size != null && page > 0 && size > 0) {
            sql.append(" LIMIT ? OFFSET ?");
            args.add(size);
            args.add((page - 1) * size);
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, total));
    }

    @PatchMapping("/tasks/{id}/done")
    public ApiResponse<Map<String, Object>> done(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        Integer owner = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM care_task WHERE id = ? AND assigned_to = ?", Integer.class, id, user.getId());
        if (owner == null || owner <= 0) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "只能完成本人任务");
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "UPDATE care_task SET status = 1, last_execute_time = ?, execute_time = ?, remark = COALESCE(?, remark) WHERE id = ?",
                now, now, remark, id
        );
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ct.id, ct.elder_id, ct.task_name, ct.status, " +
                        "DATE_FORMAT(ct.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, " +
                        "ct.assigned_to, ct.assigned_to_name, ct.frequency_type, ct.priority, " +
                        "DATE_FORMAT(ct.next_execute_time, '%Y-%m-%d %H:%i:%s') AS next_execute_time, " +
                        "DATE_FORMAT(ct.preferred_time, '%H:%i:%s') AS preferred_time, ct.instruction, " +
                        "ct.remark, DATE_FORMAT(ct.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                        "e.name AS elder_name " +
                        "FROM care_task ct LEFT JOIN elder e ON ct.elder_id = e.id WHERE ct.id = ?",
                id
        );
        return ApiResponse.success(rows.isEmpty() ? null : rows.get(0));
    }

    private LoginUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }
}

