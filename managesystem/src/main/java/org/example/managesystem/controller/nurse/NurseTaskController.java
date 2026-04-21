package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
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
}

