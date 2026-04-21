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
                "SELECT id, building, floor, room_no, room_type, bed_total, bed_occupied, status, remark, " +
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
}

