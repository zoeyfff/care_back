package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Elder;
import org.example.managesystem.entity.MedicationRecord;
import org.example.managesystem.mapper.ElderMapper;
import org.example.managesystem.mapper.MedicationRecordMapper;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@RestController
@RequestMapping("/api/nurse")
public class NurseMedicationController {

    private final JdbcTemplate jdbcTemplate;
    private final MedicationRecordMapper medicationRecordMapper;
    private final ElderMapper elderMapper;

    public NurseMedicationController(JdbcTemplate jdbcTemplate,
                                     MedicationRecordMapper medicationRecordMapper,
                                     ElderMapper elderMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.medicationRecordMapper = medicationRecordMapper;
        this.elderMapper = elderMapper;
    }

    @GetMapping("/medications")
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        Long uid = currentUserId();
        if (uid == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        // Collect all room numbers this nurse is responsible for
        Set<String> allRooms = new HashSet<>();
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room_nurse_template WHERE nurse_id = ?", String.class, uid));
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room WHERE default_nurse_id = ?", String.class, uid));

        if (allRooms.isEmpty()) {
            return ApiResponse.success(ListPage.of(java.util.Collections.emptyList(), 0));
        }
        String placeholders = String.join(",", allRooms.stream().map(r -> "?").toArray(String[]::new));

        StringBuilder where = new StringBuilder(" WHERE e.room_no IN (" + placeholders + ")");
        List<Object> args = new ArrayList<>(allRooms);
        if (status != null) {
            where.append(" AND mr.status = ?");
            args.add(status);
        }

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medication_record mr LEFT JOIN elder e ON mr.elder_id = e.id" + where,
                args.toArray(), Integer.class);
        if (total == null) total = 0;

        StringBuilder sql = new StringBuilder(
                "SELECT mr.id, mr.elder_id, e.name AS elder_name, mr.medicine_name, mr.dosage, " +
                        "mr.frequency, mr.need_confirm, mr.confirm_by, mr.confirm_by_name, " +
                        "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, " +
                        "mr.reject_reason, " +
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

    @PostMapping("/medications")
    public ApiResponse<Map<String, Object>> add(@RequestBody MedicationRecord record) {
        Long uid = currentUserId();
        if (uid == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        if (record.getStatus() == null) {
            record.setStatus(0);
        }
        if (record.getNeedConfirm() == null) {
            record.setNeedConfirm(1);
        }
        if (record.getExecuteUser() == null && record.getElderId() != null) {
            Map<String, Object> nurse = roomDefaultNurse(record.getElderId());
            if (nurse != null) {
                record.setExecuteUser((Long) nurse.get("nurse_id"));
            }
        }
        medicationRecordMapper.insert(record);
        return ApiResponse.success(toRow(record.getId()));
    }

    private Map<String, Object> toRow(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mr.id, mr.elder_id, mr.medicine_name, mr.dosage, " +
                        "mr.frequency, mr.need_confirm, mr.confirm_by, mr.confirm_by_name, " +
                        "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, " +
                        "mr.reject_reason, mr.status, mr.execute_user, " +
                        "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, " +
                        "DATE_FORMAT(mr.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, " +
                        "mr.remark, DATE_FORMAT(mr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                        "e.name AS elder_name FROM medication_record mr " +
                        "LEFT JOIN elder e ON mr.elder_id = e.id WHERE mr.id = ?",
                id
        );
        if (rows.isEmpty()) {
            MedicationRecord mr = medicationRecordMapper.selectById(id);
            Map<String, Object> m = new HashMap<>();
            if (mr != null) {
                m.put("id", mr.getId());
                m.put("elder_id", mr.getElderId());
                m.put("medicine_name", mr.getMedicineName());
                m.put("dosage", mr.getDosage());
                m.put("frequency", mr.getFrequency());
                m.put("need_confirm", mr.getNeedConfirm());
                m.put("confirm_by", mr.getConfirmBy());
                m.put("confirm_by_name", mr.getConfirmByName());
                m.put("confirm_time", mr.getConfirmTime());
                m.put("reject_reason", mr.getRejectReason());
                m.put("status", mr.getStatus());
                m.put("execute_user", mr.getExecuteUser());
                m.put("execute_time", mr.getExecuteTime());
                Elder e = elderMapper.selectById(mr.getElderId());
                m.put("elder_name", e != null ? e.getName() : null);
            }
            return m;
        }
        return rows.get(0);
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

    @PatchMapping("/medications/{id}/done")
    public ApiResponse<Map<String, Object>> done(@PathVariable Long id) {
        Long uid = currentUserId();
        if (uid == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        Set<String> allRooms = new HashSet<>();
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room_nurse_template WHERE nurse_id = ?", String.class, uid));
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room WHERE default_nurse_id = ?", String.class, uid));
        if (allRooms.isEmpty()) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "您未负责任何房间，无法执行用药任务");
        }
        String checkSql = "SELECT e.room_no FROM medication_record mr LEFT JOIN elder e ON mr.elder_id = e.id WHERE mr.id = ?";
        List<Map<String, Object>> roomRows = jdbcTemplate.queryForList(checkSql, id);
        if (roomRows.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "用药记录不存在");
        }
        Object roomVal = roomRows.get(0).get("room_no");
        String elderRoom = roomVal == null ? null : String.valueOf(roomVal);
        if (elderRoom == null || !allRooms.contains(elderRoom)) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "只能执行本人负责房间的用药任务");
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
                        "mr.frequency, mr.need_confirm, mr.confirm_by, mr.confirm_by_name, " +
                        "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, " +
                        "mr.reject_reason, " +
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

    @PatchMapping("/medications/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable Long id) {
        Long uid = currentUserId();
        if (uid == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        Set<String> allRooms = new HashSet<>();
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room_nurse_template WHERE nurse_id = ?", String.class, uid));
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room WHERE default_nurse_id = ?", String.class, uid));
        if (allRooms.isEmpty()) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "您未负责任何房间");
        }
        String checkSql = "SELECT e.room_no FROM medication_record mr LEFT JOIN elder e ON mr.elder_id = e.id WHERE mr.id = ?";
        List<Map<String, Object>> roomRows = jdbcTemplate.queryForList(checkSql, id);
        if (roomRows.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "用药记录不存在");
        }
        Object roomVal = roomRows.get(0).get("room_no");
        String elderRoom = roomVal == null ? null : String.valueOf(roomVal);
        if (elderRoom == null || !allRooms.contains(elderRoom)) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "只能确认本人负责房间的用药任务");
        }
        List<String> names = jdbcTemplate.queryForList(
                "SELECT real_name FROM `user` WHERE id = ?", String.class, uid);
        String name = names.isEmpty() ? "" : names.get(0);
        int updated = jdbcTemplate.update(
                "UPDATE medication_record SET status = 2, confirm_by = ?, confirm_by_name = ?, confirm_time = ? WHERE id = ?",
                uid, name, LocalDateTime.now(), id
        );
        if (updated <= 0) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "用药记录不存在");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mr.id, mr.elder_id, e.name AS elder_name, mr.medicine_name, mr.dosage, " +
                        "mr.frequency, mr.need_confirm, mr.confirm_by, mr.confirm_by_name, " +
                        "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, " +
                        "mr.reject_reason, " +
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

    @PatchMapping("/medications/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Long uid = currentUserId();
        if (uid == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        Set<String> allRooms = new HashSet<>();
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room_nurse_template WHERE nurse_id = ?", String.class, uid));
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room WHERE default_nurse_id = ?", String.class, uid));
        if (allRooms.isEmpty()) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "您未负责任何房间");
        }
        String checkSql = "SELECT e.room_no FROM medication_record mr LEFT JOIN elder e ON mr.elder_id = e.id WHERE mr.id = ?";
        List<Map<String, Object>> roomRows = jdbcTemplate.queryForList(checkSql, id);
        if (roomRows.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "用药记录不存在");
        }
        Object roomVal = roomRows.get(0).get("room_no");
        String elderRoom = roomVal == null ? null : String.valueOf(roomVal);
        if (elderRoom == null || !allRooms.contains(elderRoom)) {
            return ApiResponse.fail(ApiCodes.FORBIDDEN, "只能拒绝本人负责房间的用药任务");
        }
        String reason = body == null ? null : body.get("reject_reason");
        if (reason == null || reason.trim().isEmpty()) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "reject_reason 不能为空");
        }
        int updated = jdbcTemplate.update(
                "UPDATE medication_record SET status = 3, reject_reason = ? WHERE id = ?",
                reason.trim(), id
        );
        if (updated <= 0) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "用药记录不存在");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mr.id, mr.elder_id, e.name AS elder_name, mr.medicine_name, mr.dosage, " +
                        "mr.frequency, mr.need_confirm, mr.confirm_by, mr.confirm_by_name, " +
                        "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, " +
                        "mr.reject_reason, " +
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
