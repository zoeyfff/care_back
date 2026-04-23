package org.example.managesystem.controller;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/medication-records")
public class MedicationRecordController {

    private final MedicationRecordMapper medicationRecordMapper;
    private final ElderMapper elderMapper;
    private final JdbcTemplate jdbcTemplate;

    public MedicationRecordController(MedicationRecordMapper medicationRecordMapper, ElderMapper elderMapper, JdbcTemplate jdbcTemplate) {
        this.medicationRecordMapper = medicationRecordMapper;
        this.elderMapper = elderMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(value = "elder_id", required = false) Long elderId,
            @RequestParam(value = "execute_user", required = false) Long executeUser,
            @RequestParam(value = "room_no", required = false) String roomNo,
            @RequestParam(value = "status", required = false) Integer status
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT mr.id, mr.elder_id, mr.medicine_name, mr.dosage, "
                        + "mr.frequency, mr.need_confirm, mr.confirm_by, mr.confirm_by_name, "
                        + "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, "
                        + "mr.reject_reason, mr.status, mr.execute_user, "
                        + "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, "
                        + "DATE_FORMAT(mr.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, "
                        + "mr.remark, DATE_FORMAT(mr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM medication_record mr "
                        + "LEFT JOIN elder e ON mr.elder_id = e.id WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (elderId != null) {
            sql.append(" AND mr.elder_id = ?");
            args.add(elderId);
        }
        if (roomNo != null && !roomNo.trim().isEmpty()) {
            sql.append(" AND e.room_no = ?");
            args.add(roomNo.trim());
        }
        if (status != null) {
            sql.append(" AND mr.status = ?");
            args.add(status);
        }
        if (executeUser != null) {
            sql.append(" AND mr.execute_user = ?");
            args.add(executeUser);
        }
        sql.append(" ORDER BY mr.take_time DESC, mr.id DESC");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody MedicationRecord record) {
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

    @PatchMapping("/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable Long id) {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        List<String> names = jdbcTemplate.queryForList("SELECT real_name FROM `user` WHERE id = ?", String.class, user.getId());
        String name = names.isEmpty() ? user.getUsername() : names.get(0);
        int updated = jdbcTemplate.update(
                "UPDATE medication_record SET status = 2, confirm_by = ?, confirm_by_name = ?, confirm_time = ? WHERE id = ?",
                user.getId(), name, LocalDateTime.now(), id
        );
        if (updated <= 0) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "用药记录不存在");
        }
        return ApiResponse.success(toRow(id));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<Map<String, Object>> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
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
        return ApiResponse.success(toRow(id));
    }

    private Map<String, Object> toRow(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mr.id, mr.elder_id, mr.medicine_name, mr.dosage, "
                        + "mr.frequency, mr.need_confirm, mr.confirm_by, mr.confirm_by_name, "
                        + "DATE_FORMAT(mr.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirm_time, "
                        + "mr.reject_reason, mr.status, mr.execute_user, "
                        + "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, "
                        + "DATE_FORMAT(mr.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, "
                        + "mr.remark, DATE_FORMAT(mr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM medication_record mr "
                        + "LEFT JOIN elder e ON mr.elder_id = e.id WHERE mr.id = ?",
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

    private LoginUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
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
