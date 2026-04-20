package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Elder;
import org.example.managesystem.entity.HealthRecord;
import org.example.managesystem.mapper.ElderMapper;
import org.example.managesystem.mapper.HealthRecordMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health-records")
public class HealthRecordController {

    private final HealthRecordMapper healthRecordMapper;
    private final ElderMapper elderMapper;
    private final JdbcTemplate jdbcTemplate;

    public HealthRecordController(HealthRecordMapper healthRecordMapper, ElderMapper elderMapper, JdbcTemplate jdbcTemplate) {
        this.healthRecordMapper = healthRecordMapper;
        this.elderMapper = elderMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(value = "elder_id", required = false) Long elderId,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        StringBuilder sql = new StringBuilder(
                "SELECT hr.id, hr.elder_id, hr.temperature, hr.blood_pressure, hr.heart_rate, "
                        + "DATE_FORMAT(hr.record_time, '%Y-%m-%d %H:%i:%s') AS record_time, "
                        + "DATE_FORMAT(hr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM health_record hr "
                        + "LEFT JOIN elder e ON hr.elder_id = e.id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (elderId != null) {
            sql.append(" AND hr.elder_id = ?");
            args.add(elderId);
        }
        if (startTime != null && !startTime.isEmpty()) {
            sql.append(" AND hr.record_time >= ?");
            args.add(startTime);
        }
        if (endTime != null && !endTime.isEmpty()) {
            sql.append(" AND hr.record_time <= ?");
            args.add(endTime);
        }
        sql.append(" ORDER BY hr.record_time DESC, hr.id DESC");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody HealthRecord record) {
        healthRecordMapper.insert(record);
        return ApiResponse.success(toRow(record.getId()));
    }

    private Map<String, Object> toRow(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT hr.id, hr.elder_id, hr.temperature, hr.blood_pressure, hr.heart_rate, "
                        + "DATE_FORMAT(hr.record_time, '%Y-%m-%d %H:%i:%s') AS record_time, "
                        + "DATE_FORMAT(hr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM health_record hr "
                        + "LEFT JOIN elder e ON hr.elder_id = e.id WHERE hr.id = ?",
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
                Elder e = elderMapper.selectById(hr.getElderId());
                m.put("elder_name", e != null ? e.getName() : null);
            }
            return m;
        }
        return rows.get(0);
    }
}
