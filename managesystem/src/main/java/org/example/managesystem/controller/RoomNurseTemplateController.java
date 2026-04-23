package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/room-nurse-templates")
public class RoomNurseTemplateController {

    private final JdbcTemplate jdbcTemplate;

    public RoomNurseTemplateController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/batch")
    public ApiResponse<Map<String, Object>> batchSet(@RequestBody Map<String, Object> body) {
        Object roomNosObj = body.get("room_nos");
        Object nurseIdObj = body.get("nurse_id");
        if (!(roomNosObj instanceof List) || ((List<?>) roomNosObj).isEmpty()) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "room_nos 不能为空");
        }
        if (!(nurseIdObj instanceof Number)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "nurse_id 必填");
        }
        Long nurseId = ((Number) nurseIdObj).longValue();
        List<String> names = jdbcTemplate.queryForList("SELECT real_name FROM `user` WHERE id = ?", String.class, nurseId);
        if (names.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "护理员不存在");
        }
        String nurseName = names.get(0);
        int updated = 0;
        for (Object v : (List<?>) roomNosObj) {
            if (v == null) continue;
            String roomNo = String.valueOf(v).trim();
            if (roomNo.isEmpty()) continue;
            updated += jdbcTemplate.update(
                    "INSERT INTO room_nurse_template(room_no, nurse_id, nurse_name, update_time) VALUES(?,?,?,NOW()) " +
                            "ON DUPLICATE KEY UPDATE nurse_id = VALUES(nurse_id), nurse_name = VALUES(nurse_name), update_time = NOW()",
                    roomNo, nurseId, nurseName
            );
            jdbcTemplate.update("UPDATE room SET default_nurse_id = ?, default_nurse_name = ? WHERE room_no = ?", nurseId, nurseName, roomNo);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("updated", updated);
        return ApiResponse.success(data);
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT room_no, nurse_id, nurse_name, DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s') AS update_time " +
                        "FROM room_nurse_template ORDER BY room_no"
        );
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @DeleteMapping("/{roomNo}")
    public ApiResponse<Void> delete(@PathVariable String roomNo) {
        int n = jdbcTemplate.update("DELETE FROM room_nurse_template WHERE room_no = ?", roomNo);
        jdbcTemplate.update("UPDATE room SET default_nurse_id = NULL, default_nurse_name = NULL WHERE room_no = ?", roomNo);
        if (n <= 0) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "模板不存在");
        }
        return ApiResponse.success();
    }
}

