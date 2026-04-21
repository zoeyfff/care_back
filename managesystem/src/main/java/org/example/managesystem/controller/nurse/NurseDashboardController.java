package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/nurse/dashboard")
public class NurseDashboardController {

    private final JdbcTemplate jdbcTemplate;

    public NurseDashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@RequestParam(required = false) String date) {
        // date 参数目前前端可选；这里以“当前待办”口径统计即可
        int taskTodo = safeCount("SELECT COUNT(*) FROM care_task WHERE status = 0");
        int medDue = safeCount("SELECT COUNT(*) FROM medication_record WHERE status = 0");
        int vitalAbnormal = 0; // 需要业务规则（阈值）才能定义异常，这里先返回 0，避免误报
        int handoverUnread = safeCount("SELECT COUNT(*) FROM handover WHERE read_status = 0");
        Map<String, Object> data = new HashMap<>();
        data.put("taskTodo", taskTodo);
        data.put("medDue", medDue);
        data.put("vitalAbnormal", vitalAbnormal);
        data.put("handoverUnread", handoverUnread);
        return ApiResponse.success(data);
    }

    private int safeCount(String sql, Object... args) {
        try {
            Integer v = jdbcTemplate.queryForObject(sql, args, Integer.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }
}

