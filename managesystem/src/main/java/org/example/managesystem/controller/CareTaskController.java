package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.CareTask;
import org.example.managesystem.mapper.CareTaskMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) Integer status) {
        StringBuilder sql = new StringBuilder(
                "SELECT ct.id, ct.elder_id, ct.task_name, ct.status, "
                        + "DATE_FORMAT(ct.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, "
                        + "ct.remark, DATE_FORMAT(ct.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM care_task ct "
                        + "LEFT JOIN elder e ON ct.elder_id = e.id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND ct.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY ct.execute_time DESC, ct.id DESC");
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> add(@RequestBody CareTask task) {
        careTaskMapper.insert(task);
        return ApiResponse.success(rowById(task.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody CareTask task) {
        task.setId(id);
        careTaskMapper.updateById(task);
        return ApiResponse.success(rowById(id));
    }

    private Map<String, Object> rowById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ct.id, ct.elder_id, ct.task_name, ct.status, "
                        + "DATE_FORMAT(ct.execute_time, '%Y-%m-%d %H:%i:%s') AS execute_time, "
                        + "ct.remark, DATE_FORMAT(ct.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                        + "e.name AS elder_name FROM care_task ct "
                        + "LEFT JOIN elder e ON ct.elder_id = e.id WHERE ct.id = ?",
                id
        );
        return rows.isEmpty() ? null : rows.get(0);
    }
}
