package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.CareTask;
import org.example.managesystem.mapper.CareTaskMapper;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/care-tasks")
public class CareTaskController {

    private final CareTaskMapper careTaskMapper;
    private final JdbcTemplate jdbcTemplate;

    public CareTaskController(CareTaskMapper careTaskMapper, JdbcTemplate jdbcTemplate) {
        this.careTaskMapper = careTaskMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long assigned_to,
            @RequestParam(required = false) String frequency_type
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT ct.id, ct.elder_id, ct.task_name, ct.status, "
                        + "DATE_FORMAT(ct.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, "
                        + "ct.frequency_type, ct.assigned_to, ct.assigned_to_name, "
                        + "DATE_FORMAT(ct.start_date, '%Y-%m-%d') AS start_date, "
                        + "DATE_FORMAT(ct.end_date, '%Y-%m-%d') AS end_date, "
                        + "DATE_FORMAT(ct.preferred_time, '%H:%i:%s') AS preferred_time, "
                        + "ct.priority, "
                        + "DATE_FORMAT(ct.next_execute_time, '%Y-%m-%d %H:%i:%s') AS next_execute_time, "
                        + "DATE_FORMAT(ct.last_execute_time, '%Y-%m-%d %H:%i:%s') AS last_execute_time, "
                        + "ct.instruction, "
                        + "ct.remark, DATE_FORMAT(ct.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM care_task ct "
                        + "LEFT JOIN elder e ON ct.elder_id = e.id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND ct.status = ?");
            args.add(status);
        }
        if (assigned_to != null) {
            sql.append(" AND ct.assigned_to = ?");
            args.add(assigned_to);
        }
        if (frequency_type != null && !frequency_type.trim().isEmpty()) {
            sql.append(" AND ct.frequency_type = ?");
            args.add(frequency_type.trim());
        }
        sql.append(" ORDER BY ct.execute_time DESC, ct.id DESC");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody CareTask task) {
        if (task.getFrequencyType() == null || task.getFrequencyType().trim().isEmpty()) {
            task.setFrequencyType("ONCE");
        }
        if (task.getPriority() == null) {
            task.setPriority(1);
        }
        if (task.getAssignedTo() == null && task.getElderId() != null) {
            Map<String, Object> nurse = roomDefaultNurse(task.getElderId());
            if (nurse != null) {
                task.setAssignedTo((Long) nurse.get("nurse_id"));
                task.setAssignedToName((String) nurse.get("nurse_name"));
            }
        }
        careTaskMapper.insert(task);
        return ApiResponse.success(rowById(task.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody CareTask task) {
        task.setId(id);
        careTaskMapper.updateById(task);
        return ApiResponse.success(rowById(id));
    }

    @PatchMapping("/{id}/assign")
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object uidObj = body.get("assigned_to");
        if (!(uidObj instanceof Number)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "assigned_to 必填");
        }
        Long uid = ((Number) uidObj).longValue();
        List<String> names = jdbcTemplate.queryForList("SELECT real_name FROM `user` WHERE id = ?", String.class, uid);
        if (names.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "指派人员不存在");
        }
        String name = names.get(0);
        jdbcTemplate.update(
                "UPDATE care_task SET assigned_to = ?, assigned_to_name = ? WHERE id = ?",
                uid, name, id
        );
        return ApiResponse.success(rowById(id));
    }

    @PatchMapping("/batch-assign")
    public ApiResponse<Map<String, Object>> batchAssign(@RequestBody Map<String, Object> body) {
        Object taskIdsObj = body.get("task_ids");
        Object uidObj = body.get("assigned_to");
        if (!(taskIdsObj instanceof List) || ((List<?>) taskIdsObj).isEmpty()) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "task_ids 不能为空");
        }
        if (!(uidObj instanceof Number)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "assigned_to 必填");
        }
        Long uid = ((Number) uidObj).longValue();
        List<String> names = jdbcTemplate.queryForList("SELECT real_name FROM `user` WHERE id = ?", String.class, uid);
        if (names.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "指派人员不存在");
        }
        String name = names.get(0);
        int updated = 0;
        for (Object v : (List<?>) taskIdsObj) {
            if (!(v instanceof Number)) continue;
            Long tid = ((Number) v).longValue();
            updated += jdbcTemplate.update(
                    "UPDATE care_task SET assigned_to = ?, assigned_to_name = ? WHERE id = ?",
                    uid, name, tid
            );
        }
        Map<String, Object> data = new HashMap<>();
        data.put("updated", updated);
        return ApiResponse.success(data);
    }

    @PatchMapping("/{id}/execute")
    public ApiResponse<Map<String, Object>> execute(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        String remark = body == null ? null : stringVal(body.get("remark"));
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "UPDATE care_task SET status = 1, last_execute_time = ?, execute_time = ?, remark = COALESCE(?, remark) WHERE id = ?",
                now, now, remark, id
        );
        return ApiResponse.success(rowById(id));
    }

    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> todayTasks() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, elder_id, task_name, status, assigned_to, assigned_to_name, priority, " +
                        "DATE_FORMAT(next_execute_time, '%Y-%m-%d %H:%i:%s') AS next_execute_time, " +
                        "DATE_FORMAT(preferred_time, '%H:%i:%s') AS preferred_time, elder_name, room_no, care_level " +
                        "FROM v_today_tasks"
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    private Map<String, Object> rowById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ct.id, ct.elder_id, ct.task_name, ct.status, "
                        + "DATE_FORMAT(ct.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, "
                        + "ct.frequency_type, ct.assigned_to, ct.assigned_to_name, "
                        + "DATE_FORMAT(ct.start_date, '%Y-%m-%d') AS start_date, "
                        + "DATE_FORMAT(ct.end_date, '%Y-%m-%d') AS end_date, "
                        + "DATE_FORMAT(ct.preferred_time, '%H:%i:%s') AS preferred_time, "
                        + "ct.priority, "
                        + "DATE_FORMAT(ct.next_execute_time, '%Y-%m-%d %H:%i:%s') AS next_execute_time, "
                        + "DATE_FORMAT(ct.last_execute_time, '%Y-%m-%d %H:%i:%s') AS last_execute_time, "
                        + "ct.instruction, "
                        + "ct.remark, DATE_FORMAT(ct.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM care_task ct "
                        + "LEFT JOIN elder e ON ct.elder_id = e.id WHERE ct.id = ?",
                id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LoginUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }

    private String stringVal(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private Map<String, Object> roomDefaultNurse(Long elderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT t.nurse_id AS nurse_id, t.nurse_name AS nurse_name " +
                        "FROM elder e JOIN room_nurse_template t ON e.room_no = t.room_no WHERE e.id = ? LIMIT 1",
                elderId
        );
        if (rows.isEmpty()) {
            rows = jdbcTemplate.queryForList(
                    "SELECT r.default_nurse_id AS nurse_id, r.default_nurse_name AS nurse_name " +
                            "FROM elder e JOIN room r ON e.room_no = r.room_no WHERE e.id = ? LIMIT 1",
                    elderId
            );
        }
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        if (!(row.get("nurse_id") instanceof Number)) return null;
        Map<String, Object> out = new HashMap<>();
        out.put("nurse_id", ((Number) row.get("nurse_id")).longValue());
        out.put("nurse_name", row.get("nurse_name") == null ? null : String.valueOf(row.get("nurse_name")));
        return out;
    }
}
