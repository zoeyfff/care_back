package org.example.managesystem.controller;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Room;
import org.example.managesystem.mapper.RoomMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomMapper roomMapper;
    private final JdbcTemplate jdbcTemplate;

    public RoomController(RoomMapper roomMapper, JdbcTemplate jdbcTemplate) {
        this.roomMapper = roomMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (room_no LIKE ? OR building LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            args.add(kw);
            args.add(kw);
        }
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status);
        }

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room" + where, args.toArray(), Integer.class);
        if (total == null) total = 0;

        StringBuilder sql = new StringBuilder(
                "SELECT id, building, floor, room_no, room_type, bed_total, bed_occupied, status, " +
                        "default_nurse_id, default_nurse_name, remark, " +
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                        "FROM room" + where + " ORDER BY id DESC"
        );
        if (page != null && size != null && page > 0 && size > 0) {
            sql.append(" LIMIT ? OFFSET ?");
            args.add(size);
            args.add((page - 1) * size);
        }
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        return ApiResponse.success(ListPage.of(list, total));
    }

    @PostMapping
    public ApiResponse<Room> add(@RequestBody Room room) {
        if (room.getBedTotal() != null && room.getBedOccupied() != null) {
            if (room.getBedOccupied() < 0 || room.getBedOccupied() > room.getBedTotal()) {
                return ApiResponse.fail(ApiCodes.BAD_REQUEST, "bed_occupied 必须在 0~bed_total 范围内");
            }
        }
        roomMapper.insert(room);
        return ApiResponse.success(roomMapper.selectById(room.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Room> update(@PathVariable Long id, @RequestBody Room room) {
        if (room.getBedTotal() != null && room.getBedOccupied() != null) {
            if (room.getBedOccupied() < 0 || room.getBedOccupied() > room.getBedTotal()) {
                return ApiResponse.fail(ApiCodes.BAD_REQUEST, "bed_occupied 必须在 0~bed_total 范围内");
            }
        }
        room.setId(id);
        roomMapper.updateById(room);
        return ApiResponse.success(roomMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roomMapper.deleteById(id);
        return ApiResponse.success();
    }

    @PatchMapping("/assign-nurse")
    public ApiResponse<Map<String, Object>> assignNurse(@RequestBody Map<String, Object> body) {
        Object nurseIdObj = body.get("nurse_id");
        Object roomIdsObj = body.get("room_ids");
        if (!(nurseIdObj instanceof Number)) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "nurse_id 必填");
        }
        if (!(roomIdsObj instanceof List) || ((List<?>) roomIdsObj).isEmpty()) {
            return ApiResponse.fail(ApiCodes.BAD_REQUEST, "room_ids 不能为空");
        }
        Long nurseId = ((Number) nurseIdObj).longValue();
        List<String> names = jdbcTemplate.queryForList("SELECT real_name FROM `user` WHERE id = ?", String.class, nurseId);
        if (names.isEmpty()) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "护理员不存在");
        }
        String nurseName = names.get(0);

        List<?> roomIdsRaw = (List<?>) roomIdsObj;
        int success = 0;
        for (Object v : roomIdsRaw) {
            if (!(v instanceof Number)) continue;
            Long rid = ((Number) v).longValue();
            success += jdbcTemplate.update(
                    "UPDATE room SET default_nurse_id = ?, default_nurse_name = ? WHERE id = ?",
                    nurseId, nurseName, rid
            );
            // Sync to room_nurse_template using room_no from the room
            String roomNo = jdbcTemplate.queryForObject(
                    "SELECT room_no FROM room WHERE id = ?", String.class, rid);
            if (roomNo != null) {
                jdbcTemplate.update(
                        "INSERT INTO room_nurse_template(room_no, nurse_id, nurse_name, update_time) VALUES(?,?,?,NOW()) " +
                                "ON DUPLICATE KEY UPDATE nurse_id = VALUES(nurse_id), nurse_name = VALUES(nurse_name), update_time = NOW()",
                        roomNo, nurseId, nurseName
                );
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("nurse_id", nurseId);
        data.put("nurse_name", nurseName);
        data.put("updated_count", success);
        return ApiResponse.success(data);
    }
}

