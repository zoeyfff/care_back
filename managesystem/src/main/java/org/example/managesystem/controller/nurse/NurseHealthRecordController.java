package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Elder;
import org.example.managesystem.entity.HealthRecord;
import org.example.managesystem.mapper.ElderMapper;
import org.example.managesystem.mapper.HealthRecordMapper;
import org.example.managesystem.security.LoginUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/nurse")
public class NurseHealthRecordController {

    private final JdbcTemplate jdbcTemplate;
    private final HealthRecordMapper healthRecordMapper;
    private final ElderMapper elderMapper;

    public NurseHealthRecordController(JdbcTemplate jdbcTemplate,
                                       HealthRecordMapper healthRecordMapper,
                                       ElderMapper elderMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.healthRecordMapper = healthRecordMapper;
        this.elderMapper = elderMapper;
    }

    @GetMapping("/health-records")
    public ApiResponse<Map<String, Object>> myHealthRecords(
            @RequestParam(value = "elder_id", required = false) Long elderId,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime
    ) {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        Set<String> allRooms = new HashSet<>();
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room_nurse_template WHERE nurse_id = ?", String.class, user.getId()));
        allRooms.addAll(jdbcTemplate.queryForList(
                "SELECT room_no FROM room WHERE default_nurse_id = ?", String.class, user.getId()));

        if (allRooms.isEmpty()) {
            return ApiResponse.success(ListPage.of(java.util.Collections.emptyList(), 0));
        }
        String placeholders = String.join(",", allRooms.stream().map(r -> "?").toArray(String[]::new));

        StringBuilder sql = new StringBuilder(
                "SELECT hr.id, hr.elder_id, e.name AS elder_name, hr.temperature, hr.blood_pressure, hr.heart_rate, " +
                        "DATE_FORMAT(hr.record_time, '%Y-%m-%d %H:%i:%s') AS record_time, " +
                        "hr.recorded_by, hr.recorded_by_name, hr.abnormal_flag, hr.follow_up_action, " +
                        "DATE_FORMAT(hr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM health_record hr LEFT JOIN elder e ON hr.elder_id = e.id " +
                        "WHERE e.room_no IN (" + placeholders + ")"
        );
        List<Object> args = new ArrayList<>(allRooms);
        if (elderId != null) {
            sql.append(" AND hr.elder_id = ?");
            args.add(elderId);
        }
        if (startTime != null && !startTime.trim().isEmpty()) {
            sql.append(" AND hr.record_time >= ?");
            args.add(startTime.trim());
        }
        if (endTime != null && !endTime.trim().isEmpty()) {
            sql.append(" AND hr.record_time <= ?");
            args.add(endTime.trim());
        }
        sql.append(" ORDER BY hr.record_time DESC, hr.id DESC");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping("/health-records")
    public ApiResponse<Map<String, Object>> add(@RequestBody HealthRecord record) {
        LoginUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail(ApiCodes.UNAUTHORIZED, "未登录");
        }
        if (record.getElderId() != null) {
            Map<String, Object> nurse = roomDefaultNurse(record.getElderId());
            if (nurse != null) {
                record.setRecordedBy((Long) nurse.get("nurse_id"));
                record.setRecordedByName((String) nurse.get("nurse_name"));
            }
        }
        if (record.getRecordedBy() == null) {
            record.setRecordedBy(user.getId());
            record.setRecordedByName(user.getRealName() == null ? user.getUsername() : user.getRealName());
        }
        if (record.getAbnormalFlag() == null) {
            record.setAbnormalFlag(0);
        }
        healthRecordMapper.insert(record);
        return ApiResponse.success(toRow(record.getId()));
    }

    private Map<String, Object> toRow(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT hr.id, hr.elder_id, hr.temperature, hr.blood_pressure, hr.heart_rate, " +
                        "DATE_FORMAT(hr.record_time, '%Y-%m-%d %H:%i:%s') AS record_time, " +
                        "hr.recorded_by, hr.recorded_by_name, hr.abnormal_flag, hr.follow_up_action, " +
                        "DATE_FORMAT(hr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, " +
                        "e.name AS elder_name FROM health_record hr " +
                        "LEFT JOIN elder e ON hr.elder_id = e.id WHERE hr.id = ?",
                id
        );
        if (rows.isEmpty()) {
            HealthRecord hr = healthRecordMapper.selectById(id);
            Map<String, Object> m = new HashMap<>();
            if (hr != null) {
                m.put("id", hr.getId());
                m.put("elder_id", hr.getElderId());
                m.put("temperature", hr.getTemperature());
                m.put("blood_pressure", hr.getBloodPressure());
                m.put("heart_rate", hr.getHeartRate());
                m.put("recorded_by", hr.getRecordedBy());
                m.put("recorded_by_name", hr.getRecordedByName());
                m.put("abnormal_flag", hr.getAbnormalFlag());
                m.put("follow_up_action", hr.getFollowUpAction());
                Elder e = elderMapper.selectById(hr.getElderId());
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

    private LoginUser currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof LoginUser) {
            return (LoginUser) principal;
        }
        return null;
    }
}
