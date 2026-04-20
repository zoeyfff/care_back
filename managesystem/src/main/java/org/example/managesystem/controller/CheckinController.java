package org.example.managesystem.controller;

import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Checkin;
import org.example.managesystem.mapper.CheckinMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/checkins")
public class CheckinController {

    private final CheckinMapper checkinMapper;
    private final JdbcTemplate jdbcTemplate;

    public CheckinController(CheckinMapper checkinMapper, JdbcTemplate jdbcTemplate) {
        this.checkinMapper = checkinMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        String sql = "SELECT c.id, c.elder_id, DATE_FORMAT(c.start_date, '%Y-%m-%d') AS start_date, "
                + "DATE_FORMAT(c.end_date, '%Y-%m-%d') AS end_date, c.status, "
                + "DATE_FORMAT(c.create_time, '%Y-%m-%d %H:%i:%s') AS create_time, "
                + "e.name AS elder_name FROM checkin c "
                + "LEFT JOIN elder e ON c.elder_id = e.id ORDER BY c.start_date DESC, c.id DESC";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Checkin> add(@RequestBody Checkin checkin) {
        checkinMapper.insert(checkin);
        return ApiResponse.success(checkinMapper.selectById(checkin.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Checkin> update(@PathVariable Long id, @RequestBody Checkin checkin) {
        checkin.setId(id);
        checkinMapper.updateById(checkin);
        return ApiResponse.success(checkinMapper.selectById(id));
    }
}
