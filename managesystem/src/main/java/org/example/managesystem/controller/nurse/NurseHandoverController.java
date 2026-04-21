package org.example.managesystem.controller.nurse;

import org.example.managesystem.common.ApiCodes;
import org.example.managesystem.common.ApiResponse;
import org.example.managesystem.common.ListPage;
import org.example.managesystem.entity.Handover;
import org.example.managesystem.mapper.HandoverMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nurse/handovers")
public class NurseHandoverController {

    private final HandoverMapper handoverMapper;
    private final JdbcTemplate jdbcTemplate;

    public NurseHandoverController(HandoverMapper handoverMapper, JdbcTemplate jdbcTemplate) {
        this.handoverMapper = handoverMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        String sql = "SELECT h.id, DATE_FORMAT(h.shift_date, '%Y-%m-%d') AS shift_date, h.shift, " +
                "h.from_id, uf.real_name AS from_name, h.to_id, ut.real_name AS to_name, " +
                "h.content, h.read_status, DATE_FORMAT(h.create_time, '%Y-%m-%d %H:%i:%s') AS create_time " +
                "FROM handover h " +
                "LEFT JOIN `user` uf ON h.from_id = uf.id " +
                "LEFT JOIN `user` ut ON h.to_id = ut.id " +
                "ORDER BY h.shift_date DESC, h.id DESC";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        return ApiResponse.success(ListPage.of(list, list.size()));
    }

    @PostMapping
    public ApiResponse<Handover> add(@RequestBody Handover handover) {
        handoverMapper.insert(handover);
        return ApiResponse.success(handoverMapper.selectById(handover.getId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Handover> update(@PathVariable Long id, @RequestBody Handover handover) {
        handover.setId(id);
        handoverMapper.updateById(handover);
        return ApiResponse.success(handoverMapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        int n = handoverMapper.deleteById(id);
        if (n <= 0) {
            return ApiResponse.fail(ApiCodes.NOT_FOUND, "交接班不存在");
        }
        return ApiResponse.success();
    }
}

