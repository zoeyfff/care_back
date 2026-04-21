package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Elder;
import org.example.managesystem.entity.MedicationRecord;
import org.example.managesystem.mapper.ElderMapper;
import org.example.managesystem.mapper.MedicationRecordMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<Map<String, Object>> list(@RequestParam(value = "elder_id", required = false) Long elderId) {
        StringBuilder sql = new StringBuilder(
                "SELECT mr.id, mr.elder_id, mr.medicine_name, mr.dosage, "
                        + "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, "
                        + "mr.remark, DATE_FORMAT(mr.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM medication_record mr "
                        + "LEFT JOIN elder e ON mr.elder_id = e.id WHERE 1=1");
        Object[] args;
        if (elderId != null) {
            sql.append(" AND mr.elder_id = ? ORDER BY mr.take_time DESC, mr.id DESC");
            args = new Object[]{elderId};
        } else {
            sql.append(" ORDER BY mr.take_time DESC, mr.id DESC");
            args = new Object[]{};
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody MedicationRecord record) {
        if (record.getStatus() == null) {
            record.setStatus(0);
        }
        medicationRecordMapper.insert(record);
        return ApiResponse.success(toRow(record.getId()));
    }

    private Map<String, Object> toRow(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT mr.id, mr.elder_id, mr.medicine_name, mr.dosage, "
                        + "DATE_FORMAT(mr.take_time, '%Y-%m-%d %H:%i:%s') AS take_time, "
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
                Elder e = elderMapper.selectById(mr.getElderId());
                m.put("elder_name", e != null ? e.getName() : null);
            }
            return m;
        }
        return rows.get(0);
    }
}
